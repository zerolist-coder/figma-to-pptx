package com.ppttofigma.backend.service;

import com.ppttofigma.backend.dto.LayoutDTO;
import com.ppttofigma.backend.dto.MasterDTO;
import com.ppttofigma.backend.dto.PptxDataDTO;
import com.ppttofigma.backend.dto.ShapeDTO;
import com.ppttofigma.backend.dto.SlideDTO;
import com.ppttofigma.backend.dto.TextDTO;
import com.ppttofigma.backend.dto.TextParagraphDTO;
import com.ppttofigma.backend.dto.TextRunDTO;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.sl.usermodel.FillStyle;
import org.apache.poi.sl.usermodel.StrokeStyle;
import org.apache.poi.sl.usermodel.PaintStyle;
import org.apache.poi.sl.usermodel.ShapeType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.openxmlformats.schemas.drawingml.x2006.main.CTPresetGeometry2D;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;

/**
 * PPTX(슬라이드) 분석 전담 서비스.
 * <p><b>역할:</b> 업로드된 OOXML pptx를 Apache POI로 읽고, Figma 플러그인이 쓰기 쉬운 {@link PptxDataDTO} 트리로 변환한다.
 * 마스터·레이아웃·개별 슬라이드, 배경(단색/이미지), 도형(그룹/그림/자동도형), 텍스트 런/단락을 채운다.
 * <p><b>경계:</b> HTTP·파일 수신은 {@link com.ppttofigma.backend.controller.ImportController} 가 담당하고,
 * 이 클래스는 파싱·DTO 조립만 수행한다.
 */
@Service
public class PptxService {

    // POI의 getAnchor()/getPageSize()는 point 단위(1pt = 12700 EMU)를 반환한다.
    // PRD §7.3의 px = emu / 9525 공식과 동일한 96dpi 기준으로 맞추려면 pt × 4/3 한다.
    private static final double POINT_TO_PX = 4.0 / 3.0;
    /** JSON·플러그인 메시지 크기 과다 방지 (바이트) */
    private static final int MAX_IMAGE_BYTES = 15 * 1024 * 1024;

    // --- 단위·좌표: POI 앵커(pt) → DTO / 텍스트에서 쓰는 px ---

    private static double toPx(double pt) {
        return pt * POINT_TO_PX;
    }

    /**
     * pptx 한 건을 통째로 읽어 {@link PptxDataDTO}를 채운다.
     * 캔버스 크기, 마스터·레이아웃 트리, 슬라이드 목록(각 배경·도형)까지 한 번에 완성한다.
     */
    public PptxDataDTO analyzePptx(MultipartFile file) throws Exception {
        PptxDataDTO pptxData = new PptxDataDTO();
        pptxData.setFileName(file.getOriginalFilename());

        try (InputStream is = file.getInputStream();
             XMLSlideShow ppt = new XMLSlideShow(is)) {

            pptxData.setWidth(toPx(ppt.getPageSize().getWidth()));
            pptxData.setHeight(toPx(ppt.getPageSize().getHeight()));

            Map<XSLFSlideLayout, int[]> layoutToIndices = new IdentityHashMap<>();
            List<MasterDTO> masters = new ArrayList<>();
            List<XSLFSlideMaster> masterList = ppt.getSlideMasters();
            for (int mi = 0; mi < masterList.size(); mi++) {
                XSLFSlideMaster master = masterList.get(mi);
                MasterDTO masterDTO = new MasterDTO();
                masterDTO.setName(sheetPartLabel(master, "master", mi));
                masterDTO.setShapes(extractShapesFromSheet(master));
                masterDTO.setBackgroundFillHex(solidFillHexFromSheet(master));
                BackgroundBlip masterBgImg = backgroundBlipFromSheet(master);
                if (masterBgImg != null) {
                    masterDTO.setBackgroundImageBase64(masterBgImg.base64);
                    masterDTO.setBackgroundImageMimeType(masterBgImg.mime);
                }
                int li = 0;
                for (XSLFSlideLayout layout : master.getSlideLayouts()) {
                    LayoutDTO layoutDTO = new LayoutDTO();
                    layoutDTO.setName(sheetPartLabel(layout, "layout", li));
                    layoutDTO.setShapes(extractShapesFromSheet(layout));
                    String layoutBg = solidFillHexFromSheet(layout);
                    if (layoutBg == null) {
                        layoutBg = masterDTO.getBackgroundFillHex();
                    }
                    layoutDTO.setBackgroundFillHex(layoutBg);
                    BackgroundBlip layoutBgImg = backgroundBlipFromSheet(layout);
                    if (layoutBgImg != null) {
                        layoutDTO.setBackgroundImageBase64(layoutBgImg.base64);
                        layoutDTO.setBackgroundImageMimeType(layoutBgImg.mime);
                    } else if (masterBgImg != null) {
                        layoutDTO.setBackgroundImageBase64(masterBgImg.base64);
                        layoutDTO.setBackgroundImageMimeType(masterBgImg.mime);
                    }
                    masterDTO.getLayouts().add(layoutDTO);
                    layoutToIndices.put(layout, new int[] { mi, li });
                    li++;
                }
                masters.add(masterDTO);
            }
            pptxData.setMasters(masters);

            for (XSLFSlide slide : ppt.getSlides()) {
                SlideDTO slideDTO = new SlideDTO();
                slideDTO.setSlideNumber(slide.getSlideNumber());

                XSLFSlideLayout layout = slide.getSlideLayout();
                if (layout != null) {
                    int[] idx = layoutToIndices.get(layout);
                    if (idx != null) {
                        slideDTO.setMasterIndex(idx[0]);
                        slideDTO.setLayoutIndex(idx[1]);
                    }
                }
                slideDTO.setBackgroundFillHex(resolveSlideBackgroundHex(slide, layout));
                resolveSlideBackgroundBlip(slideDTO, slide, layout);
                slideDTO.setShapes(extractShapesFromSheet(slide));
                pptxData.getSlides().add(slideDTO);
            }
        }

        return pptxData;
    }

    // --- 배경: 단색(hex) 및 이미지(blip → Base64), 슬라이드 상속 순서 ---

    /**
     * 시트(슬라이드/마스터/레이아웃)의 배경이 단색 채우기일 때만 헥사 문자열.
     * 그림/그라데이션 등은 null.
     */
    private static String solidFillHexFromSheet(XSLFSheet sheet) {
        if (sheet == null) {
            return null;
        }
        try {
            XSLFBackground bg = sheet.getBackground();
            if (bg == null) {
                return null;
            }
            Color c = bg.getFillColor();
            if (c == null) {
                return null;
            }
            return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
        } catch (Exception e) {
            return null;
        }
    }

    /** PPT 상속: 슬라이드 → 레이아웃 → 마스터 순으로 단색 배경 탐색 */
    private static String resolveSlideBackgroundHex(XSLFSlide slide, XSLFSlideLayout layout) {
        String h = solidFillHexFromSheet(slide);
        if (h != null) {
            return h;
        }
        if (layout != null) {
            h = solidFillHexFromSheet(layout);
            if (h != null) {
                return h;
            }
            XSLFSlideMaster sm = layout.getSlideMaster();
            if (sm != null) {
                h = solidFillHexFromSheet(sm);
            }
        }
        return h;
    }

    /** 슬라이드 → 레이아웃 → 마스터 순으로 배경 그림(blip) 탐색 */
    private void resolveSlideBackgroundBlip(SlideDTO slideDTO, XSLFSlide slide, XSLFSlideLayout layout) {
        BackgroundBlip bi = backgroundBlipFromSheet(slide);
        if (bi != null) {
            slideDTO.setBackgroundImageBase64(bi.base64);
            slideDTO.setBackgroundImageMimeType(bi.mime);
            return;
        }
        if (layout != null) {
            bi = backgroundBlipFromSheet(layout);
            if (bi != null) {
                slideDTO.setBackgroundImageBase64(bi.base64);
                slideDTO.setBackgroundImageMimeType(bi.mime);
                return;
            }
            XSLFSlideMaster sm = layout.getSlideMaster();
            if (sm != null) {
                bi = backgroundBlipFromSheet(sm);
                if (bi != null) {
                    slideDTO.setBackgroundImageBase64(bi.base64);
                    slideDTO.setBackgroundImageMimeType(bi.mime);
                }
            }
        }
    }

    /** 시트 배경 텍스처를 Base64 문자열로 옮길 때 쓰는 내부 전달 객체. */
    private static final class BackgroundBlip {
        final String base64;
        final String mime;

        BackgroundBlip(String base64, String mime) {
            this.base64 = base64;
            this.mime = mime;
        }
    }

    /** 시트 배경이 그림 채우기일 때 Base64+MIME. 없거나 실패하면 null. */
    private BackgroundBlip backgroundBlipFromSheet(XSLFSheet sheet) {
        if (sheet == null) {
            return null;
        }
        try {
            XSLFBackground bg = sheet.getBackground();
            if (bg == null) {
                return null;
            }
            FillStyle fillStyle = bg.getFillStyle();
            if (fillStyle == null) {
                return null;
            }
            PaintStyle ps = fillStyle.getPaint();
            if (!(ps instanceof PaintStyle.TexturePaint)) {
                return null;
            }
            PaintStyle.TexturePaint tex = (PaintStyle.TexturePaint) ps;
            String mime = tex.getContentType();
            try (InputStream in = tex.getImageData()) {
                byte[] raw = in.readAllBytes();
                if (raw.length == 0) {
                    return null;
                }
                String m = (mime != null && !mime.isEmpty()) ? mime : "image/png";
                if (raw.length > MAX_IMAGE_BYTES) {
                    return null;
                }
                return new BackgroundBlip(Base64.getEncoder().encodeToString(raw), m);
            }
        } catch (IOException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** OOXML 패키지 파트 파일명 등으로 시트(마스터/레이아웃) 표시 이름을 정한다. */
    private static String sheetPartLabel(XSLFSheet sheet, String kind, int index) {
        try {
            String path = sheet.getPackagePart().getPartName().getName();
            if (path != null && !path.isEmpty()) {
                int slash = path.lastIndexOf('/');
                String base = slash >= 0 ? path.substring(slash + 1) : path;
                if (!base.isEmpty()) {
                    return base.replace(".xml", "");
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return kind + "-" + (index + 1);
    }

    // --- 도형: 그룹/그림/자동도형을 ShapeDTO 트리로, 그룹 내부 좌표 보정 포함 ---

    /**
     * 시트에서 도형 DTO 목록을 만든다.
     * 슬라이드가 아닌 시트(슬라이드 마스터·레이아웃)에서는 {@link XSLFShape#isPlaceholder() 플레이스홀더}
     * 를 제외한다. 레이아웃을 슬라이드에 인스턴스로 깔 때 마스터 기본 문구가 콘텐츠 위에 겹치는 것을 막기 위함이다.
     * 슬라이드 본문은 플레이스홀더에 적은 텍스트도 유지해야 하므로 그대로 둔다.
     */
    private List<ShapeDTO> extractShapesFromSheet(XSLFSheet sheet) {
        boolean stripPlaceholders = !(sheet instanceof XSLFSlide);
        List<ShapeDTO> out = new ArrayList<>();
        for (XSLFShape shape : sheet.getShapes()) {
            ShapeDTO dto = convertShapeToDto(shape, stripPlaceholders, null, null);
            if (dto != null) {
                out.add(dto);
            }
        }
        return out;
    }

    /**
     * 부모 그룹이 없으면 interior/outer 는 null → 앵커를 슬라이드 pt 그대로 px 변환.
     * 그룹 자식이면 interior=부모 chOff/chExt, outer=부모 그룹 앵커(pt)로 로컬 좌표로 사상 후 px.
     */
    private void applyAnchorToDto(ShapeDTO dto, Rectangle2D anchorPt, Rectangle2D interiorPt, Rectangle2D outerPt) {
        if (interiorPt == null || outerPt == null) {
            dto.setX(toPx(anchorPt.getX()));
            dto.setY(toPx(anchorPt.getY()));
            dto.setWidth(toPx(Math.max(anchorPt.getWidth(), 1e-9)));
            dto.setHeight(toPx(Math.max(anchorPt.getHeight(), 1e-9)));
            return;
        }
        double iw = interiorPt.getWidth();
        double ih = interiorPt.getHeight();
        if (iw <= 1e-9 || ih <= 1e-9) {
            dto.setX(toPx(anchorPt.getX()));
            dto.setY(toPx(anchorPt.getY()));
            dto.setWidth(toPx(Math.max(anchorPt.getWidth(), 1e-9)));
            dto.setHeight(toPx(Math.max(anchorPt.getHeight(), 1e-9)));
            return;
        }
        double sx = outerPt.getWidth() / iw;
        double sy = outerPt.getHeight() / ih;
        double x = (anchorPt.getX() - interiorPt.getX()) * sx;
        double y = (anchorPt.getY() - interiorPt.getY()) * sy;
        double w = anchorPt.getWidth() * sx;
        double h = anchorPt.getHeight() * sy;
        dto.setX(toPx(x));
        dto.setY(toPx(y));
        dto.setWidth(toPx(Math.max(w, 1e-9)));
        dto.setHeight(toPx(Math.max(h, 1e-9)));
    }

    private ShapeDTO convertShapeToDto(XSLFShape shape, boolean stripPlaceholders,
                                       Rectangle2D interiorPt, Rectangle2D outerPt) {
        if (stripPlaceholders && shape.isPlaceholder()) {
            return null;
        }
        if (shape instanceof XSLFGroupShape) {
            return groupShapeToDto((XSLFGroupShape) shape, stripPlaceholders, interiorPt, outerPt);
        }
        if (shape instanceof XSLFPictureShape) {
            return pictureShapeToDto((XSLFPictureShape) shape, interiorPt, outerPt);
        }
        if (shape instanceof XSLFSimpleShape) {
            return simpleShapeToDto((XSLFSimpleShape) shape, interiorPt, outerPt);
        }
        return null;
    }

    private ShapeDTO groupShapeToDto(XSLFGroupShape group, boolean stripPlaceholders,
                                     Rectangle2D parentInterior, Rectangle2D parentOuter) {
        Rectangle2D outer = group.getAnchor();
        Rectangle2D interior = group.getInteriorAnchor();

        ShapeDTO dto = new ShapeDTO();
        dto.setType("GROUP");
        applyAnchorToDto(dto, outer, parentInterior, parentOuter);
        dto.setRotation(group.getRotation());
        dto.setFlipHorizontal(group.getFlipHorizontal());
        dto.setFlipVertical(group.getFlipVertical());

        List<ShapeDTO> children = new ArrayList<>();
        for (XSLFShape ch : group.getShapes()) {
            ShapeDTO childDto = convertShapeToDto(ch, stripPlaceholders, interior, outer);
            if (childDto != null) {
                children.add(childDto);
            }
        }
        dto.setChildren(children);
        if (children.isEmpty()) {
            return null;
        }
        return dto;
    }

    private ShapeDTO pictureShapeToDto(XSLFPictureShape picture, Rectangle2D interiorPt, Rectangle2D outerPt) {
        ShapeDTO shapeDTO = new ShapeDTO();
        shapeDTO.setType("PICTURE");
        Rectangle2D anchor = picture.getAnchor();
        applyAnchorToDto(shapeDTO, anchor, interiorPt, outerPt);
        shapeDTO.setRotation(picture.getRotation());
        shapeDTO.setFlipHorizontal(picture.getFlipHorizontal());
        shapeDTO.setFlipVertical(picture.getFlipVertical());

        try {
            XSLFPictureData picData = picture.getPictureData();
            if (picData != null) {
                byte[] raw = picData.getData();
                if (raw != null && raw.length > 0) {
                    String mime = "image/png";
                    try {
                        if (picData.getPackagePart() != null) {
                            String ct = picData.getPackagePart().getContentType();
                            if (ct != null && !ct.isEmpty()) {
                                mime = ct;
                            }
                        }
                    } catch (Exception ignored) {
                        // keep default
                    }
                    putImagePayload(shapeDTO, raw, mime);
                }
            }
        } catch (Exception ignored) {
        }

        return shapeDTO;
    }

    /** 원시 바이트·MIME을 ShapeDTO에 넣는다 (상한 초과 시 Base64 생략). */
    private void putImagePayload(ShapeDTO shapeDTO, byte[] raw, String mimeHint) {
        if (raw == null || raw.length == 0) {
            return;
        }
        String mime = (mimeHint != null && !mimeHint.isEmpty()) ? mimeHint : "image/png";
        shapeDTO.setImageMimeType(mime);
        if (raw.length <= MAX_IMAGE_BYTES) {
            shapeDTO.setImageBase64(Base64.getEncoder().encodeToString(raw));
        }
    }

    /** 자동 도형 등 SIMPLE shape의 blip(그림) 채우기 → imageBase64. */
    private void attachTextureFillIfAny(XSLFSimpleShape simpleShape, ShapeDTO shapeDTO) {
        try {
            FillStyle fillStyle = simpleShape.getFillStyle();
            if (fillStyle == null) {
                return;
            }
            PaintStyle ps = fillStyle.getPaint();
            if (ps == null || !(ps instanceof PaintStyle.TexturePaint)) {
                return;
            }
            PaintStyle.TexturePaint tex = (PaintStyle.TexturePaint) ps;
            String mime = tex.getContentType();
            try (InputStream in = tex.getImageData()) {
                byte[] raw = in.readAllBytes();
                putImagePayload(shapeDTO, raw, mime);
            }
        } catch (IOException ignored) {
        } catch (Exception ignored) {
        }
    }

    // --- 자동도형 타입: POI ShapeType + DrawingML preset geometry(prst) 보강 ---

    /**
     * POI {@link ShapeType} 이 RECT 로만 나오는 경우에도, drawingml prst(예: quadArrow)로 실제 프리셋을 판별한다.
     */
    private static String readPresetGeometryName(XSLFAutoShape autoShape) {
        try {
            if (!(autoShape.getXmlObject() instanceof CTShape)) {
                return null;
            }
            CTShape ct = (CTShape) autoShape.getXmlObject();
            CTShapeProperties sp = ct.getSpPr();
            if (sp == null) {
                return null;
            }
            CTPresetGeometry2D geom = sp.getPrstGeom();
            if (geom == null) {
                return null;
            }
            if (geom.getPrst() == null) {
                return null;
            }
            return geom.getPrst().toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String mapAutoShapeDtoType(XSLFAutoShape autoShape) {
        String prst = readPresetGeometryName(autoShape);
        if (prst != null && !prst.isEmpty()) {
            String p = prst.replace("_", "").toLowerCase(Locale.ROOT);
            if (p.contains("quadarrow")) {
                return "QUAD_ARROW";
            }
            if ("leftarrow".equals(p)) {
                return "LEFT_ARROW";
            }
            if ("rightarrow".equals(p)
                    || "stripedrightarrow".equals(p)
                    || "notchedrightarrow".equals(p)
                    || "thickarrow".equals(p)) {
                return "RIGHT_ARROW";
            }
        }

        ShapeType type = autoShape.getShapeType();
        if (type == ShapeType.RECT) {
            return "RECT";
        } else if (type == ShapeType.ELLIPSE) {
            return "ELLIPSE";
        } else if (type == ShapeType.TRIANGLE) {
            return "TRIANGLE";
        } else if (type == ShapeType.LEFT_ARROW) {
            return "LEFT_ARROW";
        } else if (type == ShapeType.RIGHT_ARROW
                || type == ShapeType.THICK_ARROW
                || type == ShapeType.NOTCHED_RIGHT_ARROW
                || type == ShapeType.STRIPED_RIGHT_ARROW) {
            return "RIGHT_ARROW";
        } else if (type == ShapeType.QUAD_ARROW || type == ShapeType.QUAD_ARROW_CALLOUT) {
            return "QUAD_ARROW";
        } else {
            return "RECT";
        }
    }

    private ShapeDTO simpleShapeToDto(XSLFSimpleShape simpleShape, Rectangle2D interiorPt, Rectangle2D outerPt) {
        ShapeDTO shapeDTO = new ShapeDTO();

        Rectangle2D anchor = simpleShape.getAnchor();
        applyAnchorToDto(shapeDTO, anchor, interiorPt, outerPt);
        shapeDTO.setRotation(simpleShape.getRotation());
        shapeDTO.setFlipHorizontal(simpleShape.getFlipHorizontal());
        shapeDTO.setFlipVertical(simpleShape.getFlipVertical());

        if (simpleShape instanceof XSLFAutoShape) {
            XSLFAutoShape autoShape = (XSLFAutoShape) simpleShape;
            shapeDTO.setType(mapAutoShapeDtoType(autoShape));

            try {
                String xml = autoShape.getXmlObject().toString();
                java.util.List<Double> adjustValues = new java.util.ArrayList<>();
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

        attachTextureFillIfAny(simpleShape, shapeDTO);

        Color fillColor = simpleShape.getFillColor();
        if (fillColor != null && shapeDTO.getImageBase64() == null) {
            shapeDTO.setFillHex(String.format("#%02x%02x%02x",
                fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue()));
        }

        Color lineColor = simpleShape.getLineColor();
        if (lineColor != null) {
            shapeDTO.setStrokeHex(String.format("#%02x%02x%02x",
                lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue()));
            shapeDTO.setStrokeWeight(toPx(simpleShape.getLineWidth()));

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
                if (shapeDTO.getFillHex() == null && shapeDTO.getImageBase64() == null
                    && "TEXT_BOX".equals(shapeDTO.getType())) {
                    shapeDTO.setType("TEXT");
                }
            }
        }

        return shapeDTO;
    }

    // --- 텍스트: 단락/런·정렬·폰트·색 → TextDTO (플러그인 Text 노드에 반영) ---

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
