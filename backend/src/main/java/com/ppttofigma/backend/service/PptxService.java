package com.ppttofigma.backend.service;

import com.ppttofigma.backend.dto.PptxDataDTO;
import com.ppttofigma.backend.dto.ShapeDTO;
import com.ppttofigma.backend.dto.SlideDTO;
import com.ppttofigma.backend.dto.TextDTO;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.sl.usermodel.ShapeType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;

@Service
public class PptxService {

    public PptxDataDTO analyzePptx(MultipartFile file) throws Exception {
        PptxDataDTO pptxData = new PptxDataDTO();
        pptxData.setFileName(file.getOriginalFilename());

        try (InputStream is = file.getInputStream();
             XMLSlideShow ppt = new XMLSlideShow(is)) {

            // 슬라이드 크기 설정
            pptxData.setWidth(ppt.getPageSize().getWidth());
            pptxData.setHeight(ppt.getPageSize().getHeight());

            for (XSLFSlide slide : ppt.getSlides()) {
                SlideDTO slideDTO = new SlideDTO();
                slideDTO.setSlideNumber(slide.getSlideNumber());

                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFSimpleShape) {
                        XSLFSimpleShape simpleShape = (XSLFSimpleShape) shape;
                        ShapeDTO shapeDTO = new ShapeDTO();
                        
                        // 위치 및 크기
                        Rectangle2D anchor = simpleShape.getAnchor();
                        shapeDTO.setX(anchor.getX());
                        shapeDTO.setY(anchor.getY());
                        shapeDTO.setWidth(anchor.getWidth());
                        shapeDTO.setHeight(anchor.getHeight());
                        shapeDTO.setRotation(simpleShape.getRotation()); // 회전 각도 추출

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
                            shapeDTO.setStrokeWeight(simpleShape.getLineWidth());
                            
                            // 점선 스타일 추출
                            org.apache.poi.sl.usermodel.StrokeStyle.LineDash dash = simpleShape.getLineDash();
                            if (dash != null) {
                                shapeDTO.setDashStyle(dash.name());
                            }
                        }

                        // 텍스트 추출
                        if (simpleShape instanceof XSLFTextShape) {
                            XSLFTextShape textShape = (XSLFTextShape) simpleShape;
                            String textContent = textShape.getText();
                            if (textContent != null && !textContent.trim().isEmpty()) {
                                TextDTO textDTO = new TextDTO();
                                textDTO.setContent(textContent);
                                
                                // 단락 정보 추출 (정렬 등)
                                if (!textShape.getTextParagraphs().isEmpty()) {
                                    XSLFTextParagraph paragraph = textShape.getTextParagraphs().get(0);
                                    if (paragraph.getTextAlign() != null) {
                                        textDTO.setAlign(paragraph.getTextAlign().name()); // LEFT, CENTER, RIGHT, JUSTIFY
                                    }

                                    // 폰트 정보 추출
                                    if (!paragraph.getTextRuns().isEmpty()) {
                                        XSLFTextRun run = paragraph.getTextRuns().get(0);
                                        textDTO.setFontSize(run.getFontSize());
                                        
                                        // 폰트 색상 추출 (PaintStyle -> Color 변환)
                                        org.apache.poi.sl.usermodel.PaintStyle fontPaint = run.getFontColor();
                                        if (fontPaint instanceof org.apache.poi.sl.usermodel.PaintStyle.SolidPaint) {
                                            Color textColor = ((org.apache.poi.sl.usermodel.PaintStyle.SolidPaint) fontPaint).getSolidColor().getColor();
                                            if (textColor != null) {
                                                textDTO.setColorHex(String.format("#%02x%02x%02x", 
                                                    textColor.getRed(), textColor.getGreen(), textColor.getBlue()));
                                            }
                                        }
                                    }
                                }
                                shapeDTO.setText(textDTO);
                                
                                // 텍스트만 있는 경우 타입을 TEXT로 강제
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

        return pptxData;
    }
}
