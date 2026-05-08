package com.ppttofigma.backend.service;

import com.ppttofigma.backend.ImportBuildInfo;
import com.ppttofigma.backend.dto.PptxDataDTO;
import com.ppttofigma.backend.dto.ShapeDTO;
import com.ppttofigma.backend.dto.SlideDTO;
import com.ppttofigma.backend.dto.TextDTO;
import com.ppttofigma.backend.dto.TextParagraphDTO;
import com.ppttofigma.backend.dto.TextRunDTO;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.sl.usermodel.StrokeStyle;
import org.apache.poi.sl.usermodel.PaintStyle;
import org.apache.poi.sl.usermodel.ShapeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;

@Service
public class PptxService {

    private static final Logger log = LoggerFactory.getLogger(PptxService.class);

    // POI의 getAnchor()/getPageSize()는 point 단위(1pt = 12700 EMU)를 반환한다.
    // PRD §7.3의 px = emu / 9525 공식과 동일한 96dpi 기준으로 맞추려면 pt × 4/3 한다.
    private static final double POINT_TO_PX = 4.0 / 3.0;

    private static double toPx(double pt) {
        return pt * POINT_TO_PX;
    }

    public PptxDataDTO analyzePptx(MultipartFile file) throws Exception {
        String tag = ImportBuildInfo.label();
        String fn = file.getOriginalFilename() != null ? file.getOriginalFilename() : "(이름 없음)";
        log.info("[PPT→Figma {}] {} | 2. POI XMLSlideShow 로드 · 도형 추출 시작", tag, fn);

        PptxDataDTO pptxData = new PptxDataDTO();
        pptxData.setFileName(file.getOriginalFilename());

        try (InputStream is = file.getInputStream();
             XMLSlideShow ppt = new XMLSlideShow(is)) {

            pptxData.setWidth(toPx(ppt.getPageSize().getWidth()));
            pptxData.setHeight(toPx(ppt.getPageSize().getHeight()));

            for (XSLFSlide slide : ppt.getSlides()) {
                SlideDTO slideDTO = new SlideDTO();
                slideDTO.setSlideNumber(slide.getSlideNumber());

                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFSimpleShape) {
                        XSLFSimpleShape simpleShape = (XSLFSimpleShape) shape;
                        ShapeDTO shapeDTO = new ShapeDTO();

                        Rectangle2D anchor = simpleShape.getAnchor();
                        shapeDTO.setX(toPx(anchor.getX()));
                        shapeDTO.setY(toPx(anchor.getY()));
                        shapeDTO.setWidth(toPx(anchor.getWidth()));
                        shapeDTO.setHeight(toPx(anchor.getHeight()));
                        shapeDTO.setRotation(simpleShape.getRotation());
                        shapeDTO.setFlipHorizontal(simpleShape.getFlipHorizontal());
                        shapeDTO.setFlipVertical(simpleShape.getFlipVertical());

                        // 도형 타입 판별
                        if (simpleShape instanceof XSLFAutoShape) {
                            XSLFAutoShape autoShape = (XSLFAutoShape) simpleShape;
                            ShapeType type = autoShape.getShapeType();
                            if (type == ShapeType.RECT) {
                                shapeDTO.setType("RECT");
                            } else if (type == ShapeType.ELLIPSE) {
                                shapeDTO.setType("ELLIPSE");
                            } else if (type == ShapeType.TRIANGLE) {
                                shapeDTO.setType("TRIANGLE");
                            } else if (type == ShapeType.RIGHT_ARROW) {
                                shapeDTO.setType("RIGHT_ARROW");
                            } else {
                                shapeDTO.setType("RECT"); // 기본값
                            }

                            // 조정값(Adjust Value) 추출 - 가장 확실한 XML 문자열 파싱 방식
                            try {
                                String xml = autoShape.getXmlObject().toString();
                                java.util.List<Double> adjustValues = new java.util.ArrayList<>();
                                
                                // fmla="val 50000" 형태의 문자열에서 숫자만 추출하는 정규식
                                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("fmla=\"val (\\d+)\"");
                                java.util.regex.Matcher matcher = pattern.matcher(xml);
                                
                                while (matcher.find()) {
                                    adjustValues.add(Double.parseDouble(matcher.group(1)));
                                }
                                shapeDTO.setAdjustValues(adjustValues);
                            } catch (Exception e) {
                                // 무시
                            }
                        } else if (simpleShape instanceof XSLFTextShape) {
                            shapeDTO.setType("TEXT_BOX");
                        }

                        // 채우기 색상 (헥사코드 변환)
                        Color fillColor = simpleShape.getFillColor();
                        if (fillColor != null) {
                            shapeDTO.setFillHex(String.format("#%02x%02x%02x", 
                                fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue()));
                        }

                        // 테두리(선) 정보 추출
                        Color lineColor = simpleShape.getLineColor();
                        if (lineColor != null) {
                            shapeDTO.setStrokeHex(String.format("#%02x%02x%02x", 
                                lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue()));
                            shapeDTO.setStrokeWeight(toPx(simpleShape.getLineWidth()));
                            
                            // 점선 스타일 추출 (POI가 ppt 선 두께와 곱할 pattern 제공)
                            StrokeStyle.LineDash dash = simpleShape.getLineDash();
                            if (dash != null) {
                                shapeDTO.setDashStyle(dash.name());
                                if (dash.pattern != null && dash.pattern.length > 0) {
                                    java.util.ArrayList<Integer> mults = new java.util.ArrayList<>();
                                    for (int v : dash.pattern) {
                                        mults.add(v);
                                    }
                                    shapeDTO.setDashPatternMultipliers(mults);
                                }
                            }
                        }

                        if (simpleShape instanceof XSLFTextShape) {
                            XSLFTextShape textShape = (XSLFTextShape) simpleShape;
                            TextDTO textDTO = extractText(textShape);
                            if (textDTO != null) {
                                shapeDTO.setText(textDTO);

                                // 텍스트만 있고 채우기 색이 없는 경우 타입을 TEXT로 강제
                                if (shapeDTO.getFillHex() == null && "TEXT_BOX".equals(shapeDTO.getType())) {
                                    shapeDTO.setType("TEXT");
                                }
                            }
                        }

                        slideDTO.getShapes().add(shapeDTO);
                    }
                }
                pptxData.getSlides().add(slideDTO);
            }
        }

        log.info("[PPT→Figma {}] {} | 3. POI 파싱 루프 종료 (슬라이드 {}장)", tag, fn, pptxData.getSlides().size());
        return pptxData;
    }

    /**
     * 텍스트 박스의 모든 단락과 run을 추출한다.
     * 단락 사이에는 줄바꿈을 넣어 content를 합성한다.
     * 텍스트가 비어 있으면 null을 반환한다.
     */
    private TextDTO extractText(XSLFTextShape textShape) {
        java.util.List<XSLFTextParagraph> paragraphs = textShape.getTextParagraphs();
        if (paragraphs == null || paragraphs.isEmpty()) {
            return null;
        }

        TextDTO textDTO = new TextDTO();
        StringBuilder fullText = new StringBuilder();
        boolean hasAnyText = false;

        for (int i = 0; i < paragraphs.size(); i++) {
            XSLFTextParagraph paragraph = paragraphs.get(i);
            TextParagraphDTO paragraphDTO = new TextParagraphDTO();
            if (paragraph.getTextAlign() != null) {
                paragraphDTO.setAlign(paragraph.getTextAlign().name());
            }

            StringBuilder paragraphText = new StringBuilder();
            for (XSLFTextRun run : paragraph.getTextRuns()) {
                String runText = run.getRawText();
                if (runText == null) {
                    runText = "";
                }
                TextRunDTO runDTO = new TextRunDTO();
                runDTO.setText(runText);
                runDTO.setFontFamily(run.getFontFamily());
                Double fontSize = run.getFontSize();
                if (fontSize != null) {
                    runDTO.setFontSize(toPx(fontSize));
                }
                runDTO.setBold(run.isBold());
                runDTO.setItalic(run.isItalic());
                runDTO.setUnderline(run.isUnderlined());

                PaintStyle fontPaint = run.getFontColor();
                if (fontPaint instanceof PaintStyle.SolidPaint) {
                    Color textColor = ((PaintStyle.SolidPaint) fontPaint).getSolidColor().getColor();
                    if (textColor != null) {
                        runDTO.setColorHex(String.format("#%02x%02x%02x",
                            textColor.getRed(), textColor.getGreen(), textColor.getBlue()));
                    }
                }

                paragraphDTO.getRuns().add(runDTO);
                paragraphText.append(runText);
                if (!runText.isEmpty()) {
                    hasAnyText = true;
                }
            }

            if (i > 0) {
                fullText.append('\n');
            }
            fullText.append(paragraphText);
            textDTO.getParagraphs().add(paragraphDTO);
        }

        if (!hasAnyText) {
            return null;
        }

        textDTO.setContent(fullText.toString());

        // 호환을 위해 단순 필드도 첫 단락의 첫 run 기준으로 채워둔다.
        TextParagraphDTO firstParagraph = textDTO.getParagraphs().get(0);
        textDTO.setAlign(firstParagraph.getAlign());
        if (!firstParagraph.getRuns().isEmpty()) {
            TextRunDTO firstRun = firstParagraph.getRuns().get(0);
            textDTO.setFontSize(firstRun.getFontSize());
            textDTO.setColorHex(firstRun.getColorHex());
        }
        return textDTO;
    }
}
