package com.ppttofigma.backend.service;

import com.ppttofigma.backend.dto.ImageFillStyleDTO;
import com.ppttofigma.backend.dto.LayoutDTO;
import com.ppttofigma.backend.dto.MasterDTO;
import com.ppttofigma.backend.dto.PptxDataDTO;
import com.ppttofigma.backend.dto.ShapeDTO;
import com.ppttofigma.backend.dto.SlideDTO;
import com.ppttofigma.backend.dto.TextDTO;
import com.ppttofigma.backend.dto.TextParagraphDTO;
import com.ppttofigma.backend.dto.TextRunDTO;
import com.ppttofigma.backend.dto.TableCellPieceDTO;
import com.ppttofigma.backend.dto.TableDataDTO;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.util.Units;
import org.apache.poi.sl.usermodel.FillStyle;
import org.apache.poi.sl.usermodel.StrokeStyle;
import org.apache.poi.sl.usermodel.PaintStyle;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TableCell;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.drawingml.x2006.main.CTColor;
import org.openxmlformats.schemas.drawingml.x2006.main.CTColorScheme;
import org.openxmlformats.schemas.drawingml.x2006.main.CTOfficeStyleSheet;
import org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun;
import org.openxmlformats.schemas.drawingml.x2006.main.CTSchemeColor;
import org.openxmlformats.schemas.drawingml.x2006.main.CTSolidColorFillProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraph;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextField;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextLineBreak;
import org.openxmlformats.schemas.drawingml.x2006.main.STTextUnderlineType;
import org.openxmlformats.schemas.drawingml.x2006.main.STTextAlignType;
import org.openxmlformats.schemas.drawingml.x2006.main.STTextAnchoringType;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextBodyProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextBody;
import org.openxmlformats.schemas.drawingml.x2006.main.CTBlipFillProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTRelativeRect;
import org.openxmlformats.schemas.drawingml.x2006.main.CTStretchInfoProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTileInfoProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPresetGeometry2D;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties;
import org.openxmlformats.schemas.presentationml.x2006.main.CTBackground;
import org.openxmlformats.schemas.presentationml.x2006.main.CTBackgroundProperties;
import org.openxmlformats.schemas.presentationml.x2006.main.CTConnector;
import org.openxmlformats.schemas.presentationml.x2006.main.CTPicture;
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
            int cw = Math.max(1, (int) Math.round(pptxData.getWidth()));
            int ch = Math.max(1, (int) Math.round(pptxData.getHeight()));

            Map<XSLFSlideLayout, int[]> layoutToIndices = new IdentityHashMap<>();
            List<MasterDTO> masters = new ArrayList<>();
            List<XSLFSlideMaster> masterList = ppt.getSlideMasters();
            for (int mi = 0; mi < masterList.size(); mi++) {
                XSLFSlideMaster master = masterList.get(mi);
                MasterDTO masterDTO = new MasterDTO();
                masterDTO.setName(sheetPartLabel(master, "master", mi));
                masterDTO.setShapes(extractShapesFromSheet(master));
                masterDTO.setBackgroundFillHex(solidFillHexFromSheet(master));
                boolean masterAdvBg = sheetBackgroundHasAdvancedPaint(master);
                masterDTO.setBackgroundAdvancedFill(masterAdvBg);
                BackgroundBlipBundle masterBgImg = backgroundBlipFromSheet(master, cw, ch);
                if (masterBgImg != null) {
                    masterDTO.setBackgroundImageBase64(masterBgImg.base64);
                    masterDTO.setBackgroundImageMimeType(masterBgImg.mime);
                    masterDTO.setBackgroundImageFillStyle(masterBgImg.fillStyle);
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
                    BackgroundBlipBundle layoutBgImg = backgroundBlipFromSheet(layout, cw, ch);
                    if (layoutBgImg != null) {
                        layoutDTO.setBackgroundImageBase64(layoutBgImg.base64);
                        layoutDTO.setBackgroundImageMimeType(layoutBgImg.mime);
                        layoutDTO.setBackgroundImageFillStyle(layoutBgImg.fillStyle);
                    } else if (masterBgImg != null) {
                        layoutDTO.setBackgroundImageBase64(masterBgImg.base64);
                        layoutDTO.setBackgroundImageMimeType(masterBgImg.mime);
                        layoutDTO.setBackgroundImageFillStyle(masterBgImg.fillStyle);
                    }
                    layoutDTO.setBackgroundAdvancedFill(
                            sheetBackgroundHasAdvancedPaint(layout) || masterAdvBg);
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
                resolveSlideBackgroundBlip(slideDTO, slide, layout, cw, ch);
                slideDTO.setShapes(extractShapesFromSheet(slide));
                pptxData.getSlides().add(slideDTO);
            }
            pptxData.setUsedFontFamilies(computeUsedFontFamilies(pptxData));
        }

        return pptxData;
    }

    /** 슬라이드·마스터·레이아웃 텍스트 run 에서 폰트 패밀리를 모은다(대소문자 무시 중복 제거 후 정렬). */
    private List<String> computeUsedFontFamilies(PptxDataDTO pptxData) {
        List<String> acc = new ArrayList<>();
        for (SlideDTO s : pptxData.getSlides()) {
            collectFontsFromShapes(s.getShapes(), acc);
        }
        for (MasterDTO m : pptxData.getMasters()) {
            collectFontsFromShapes(m.getShapes(), acc);
            for (LayoutDTO lay : m.getLayouts()) {
                collectFontsFromShapes(lay.getShapes(), acc);
            }
        }
        List<String> uniq = new ArrayList<>();
        for (String f : acc) {
            addFontFamilyDistinct(uniq, f);
        }
        uniq.sort(String.CASE_INSENSITIVE_ORDER);
        return uniq;
    }

    private static void addFontFamilyDistinct(List<String> uniq, String raw) {
        if (raw == null) {
            return;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return;
        }
        for (String e : uniq) {
            if (e.equalsIgnoreCase(t)) {
                return;
            }
        }
        uniq.add(t);
    }

    private static void collectFontsFromShapes(List<ShapeDTO> shapes, List<String> acc) {
        if (shapes == null) {
            return;
        }
        for (ShapeDTO sh : shapes) {
            collectFontsFromShape(sh, acc);
        }
    }

    private static void collectFontsFromShape(ShapeDTO shape, List<String> acc) {
        TextDTO txt = shape.getText();
        if (txt != null) {
            collectFontsFromText(txt, acc);
        }
        if (shape.getTable() != null && shape.getTable().getPieces() != null) {
            for (TableCellPieceDTO p : shape.getTable().getPieces()) {
                if (p.getText() != null) {
                    collectFontsFromText(p.getText(), acc);
                }
            }
        }
        collectFontsFromShapes(shape.getChildren(), acc);
    }

    private static void collectFontsFromText(TextDTO txt, List<String> acc) {
        if (txt.getParagraphs() == null) {
            return;
        }
        for (TextParagraphDTO p : txt.getParagraphs()) {
            if (p.getRuns() == null) {
                continue;
            }
            for (TextRunDTO r : p.getRuns()) {
                String ff = r.getFontFamily();
                if (ff == null) {
                    continue;
                }
                String t = ff.trim();
                if (!t.isEmpty()) {
                    acc.add(t);
                }
            }
        }
    }

    // --- 배경: 단색(hex) 및 이미지(blip → Base64), 슬라이드 상속 순서 ---

    /**
     * OOXML {@code p:bg/bgPr} 에 단색·블립(blipFill) 외 채우기가 있으면 true.
     * 그라데이션 마스터는 {@link #solidFillHexFromSheet} 이 null 만 주므로 플러그인이 마스터 페이지를 만들지 못하지 않도록 쓴다.
     */
    private static boolean sheetBackgroundHasAdvancedPaint(XSLFSheet sheet) {
        if (sheet == null) {
            return false;
        }
        try {
            XSLFBackground bg = sheet.getBackground();
            if (bg == null) {
                return false;
            }
            XmlObject xo = bg.getXmlObject();
            if (!(xo instanceof CTBackground)) {
                return false;
            }
            CTBackground ctBg = (CTBackground) xo;
            if (!ctBg.isSetBgPr()) {
                return false;
            }
            CTBackgroundProperties pr = ctBg.getBgPr();
            if (pr == null) {
                return false;
            }
            return pr.isSetGradFill() || pr.isSetPattFill() || pr.isSetGrpFill();
        } catch (Exception e) {
            return false;
        }
    }

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
    private void resolveSlideBackgroundBlip(
            SlideDTO slideDTO,
            XSLFSlide slide,
            XSLFSlideLayout layout,
            int destWpx,
            int destHpx) {
        BackgroundBlipBundle bi = backgroundBlipFromSheet(slide, destWpx, destHpx);
        if (bi != null) {
            applyBackgroundBundleToSlide(slideDTO, bi);
            return;
        }
        if (layout != null) {
            bi = backgroundBlipFromSheet(layout, destWpx, destHpx);
            if (bi != null) {
                applyBackgroundBundleToSlide(slideDTO, bi);
                return;
            }
            XSLFSlideMaster sm = layout.getSlideMaster();
            if (sm != null) {
                bi = backgroundBlipFromSheet(sm, destWpx, destHpx);
                if (bi != null) {
                    applyBackgroundBundleToSlide(slideDTO, bi);
                }
            }
        }
    }

    private static void applyBackgroundBundleToSlide(SlideDTO slideDTO, BackgroundBlipBundle bi) {
        slideDTO.setBackgroundImageBase64(bi.base64);
        slideDTO.setBackgroundImageMimeType(bi.mime);
        slideDTO.setBackgroundImageFillStyle(bi.fillStyle);
    }

    /** 시트 배경 텍스처를 Base64 문자열로 옮길 때 쓰는 내부 전달 객체. */
    private static final class BackgroundBlipBundle {
        final String base64;
        final String mime;
        final ImageFillStyleDTO fillStyle;

        BackgroundBlipBundle(String base64, String mime, ImageFillStyleDTO fillStyle) {
            this.base64 = base64;
            this.mime = mime;
            this.fillStyle = fillStyle;
        }
    }

    /** 결과 DTO 채우기 없이 순수 처리용 (블립 채우기 공통 파이프라인). */
    private static final class BlipPipelineOutcome {
        final byte[] bytes;
        final String mime;
        final ImageFillStyleDTO tileStyle;

        BlipPipelineOutcome(byte[] bytes, String mime, ImageFillStyleDTO tileStyle) {
            this.bytes = bytes;
            this.mime = mime;
            this.tileStyle = tileStyle;
        }
    }

    /** 시트 배경이 그림 채우기일 때 Base64+MIME(+tile 힌트). 없거나 실패하면 null. */
    private BackgroundBlipBundle backgroundBlipFromSheet(XSLFSheet sheet, int destWpx, int destHpx) {
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
                CTBlipFillProperties bf = readBlipFillFromBackground(bg);
                BlipPipelineOutcome pipe = processBlipPipeline(raw, bf, destWpx, destHpx, m);
                if (pipe.bytes.length > MAX_IMAGE_BYTES) {
                    return null;
                }
                return new BackgroundBlipBundle(
                        Base64.getEncoder().encodeToString(pipe.bytes),
                        pipe.mime,
                        pipe.tileStyle);
            }
        } catch (IOException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static CTBlipFillProperties readBlipFillFromBackground(XSLFBackground bg) {
        try {
            XmlObject bx = bg.getXmlObject();
            if (!(bx instanceof CTBackground)) {
                return null;
            }
            CTBackground ctBg = (CTBackground) bx;
            if (!ctBg.isSetBgPr()) {
                return null;
            }
            CTBackgroundProperties pr = ctBg.getBgPr();
            if (!pr.isSetBlipFill()) {
                return null;
            }
            return pr.getBlipFill();
        } catch (Exception e) {
            return null;
        }
    }

    /** `p:pic` 의 `blipFill`. */
    private static CTBlipFillProperties readBlipFillFromPicture(XSLFPictureShape picture) {
        try {
            XmlObject xo = picture.getXmlObject();
            if (!(xo instanceof CTPicture)) {
                return null;
            }
            return ((CTPicture) xo).getBlipFill();
        } catch (Exception e) {
            return null;
        }
    }

    /** `p:sp` / `p:cxnSp` 의 `blipFill`. */
    private static CTBlipFillProperties readBlipFillFromSimpleShape(XSLFSimpleShape shape) {
        try {
            CTShapeProperties sp = resolveShapeSpPr(shape.getXmlObject());
            if (sp == null || !sp.isSetBlipFill()) {
                return null;
            }
            return sp.getBlipFill();
        } catch (Exception e) {
            return null;
        }
    }

    /** srcRect 크롭 → stretch+fillRect 9-patch(가능하면) → tile 메타 조립 */
    private static BlipPipelineOutcome processBlipPipeline(
            byte[] raw,
            CTBlipFillProperties bf,
            int destWpx,
            int destHpx,
            String defaultMime) {
        byte[] cur = raw != null ? raw : new byte[0];
        String mime = defaultMime != null && !defaultMime.isEmpty() ? defaultMime : "image/png";

        if (bf != null && bf.isSetSrcRect() && isMeaningfulCropRect(bf.getSrcRect())) {
            byte[] cropped = applySrcRectCropToRaster(cur, bf.getSrcRect());
            if (cropped != cur) {
                cur = cropped;
                mime = "image/png";
            }
        }

        byte[] stretched = applyStretchNineSlicePng(cur, bf, destWpx, destHpx);
        if (stretched != null) {
            cur = stretched;
            mime = "image/png";
        }

        ImageFillStyleDTO tileStyle = null;
        if (stretched == null && bf != null && bf.isSetTile()) {
            tileStyle = tileFillStyleFromBlip(bf);
        }

        return new BlipPipelineOutcome(cur, mime, tileStyle);
    }

    private static ImageFillStyleDTO tileFillStyleFromBlip(CTBlipFillProperties bf) {
        CTTileInfoProperties tile = bf.getTile();
        if (tile == null) {
            return null;
        }
        double sxv = Double.NaN;
        double syv = Double.NaN;
        try {
            if (tile.isSetSx()) {
                sxv = stPctCropInt(tile.getSx()) / 100000.0;
                if (sxv <= 0) {
                    sxv = Double.NaN;
                }
            }
            if (tile.isSetSy()) {
                syv = stPctCropInt(tile.getSy()) / 100000.0;
                if (syv <= 0) {
                    syv = Double.NaN;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        double factor;
        if (!Double.isNaN(sxv) && !Double.isNaN(syv)) {
            factor = Math.sqrt(sxv * syv);
        } else if (!Double.isNaN(sxv)) {
            factor = sxv;
        } else if (!Double.isNaN(syv)) {
            factor = syv;
        } else {
            factor = 1.0;
        }
        if (factor <= 1e-9) {
            factor = 1.0;
        }
        ImageFillStyleDTO dto = new ImageFillStyleDTO();
        dto.setScaleMode("TILE");
        dto.setFigmaTileScalingFactor(factor);
        return dto;
    }

    /** stretch 의 fillRect 9-patch. 실패 또는 미적용 시 null. */
    private static byte[] applyStretchNineSlicePng(byte[] cur, CTBlipFillProperties bf, int dw, int dh) {
        if (bf == null || !bf.isSetStretch()) {
            return null;
        }
        CTStretchInfoProperties st = bf.getStretch();
        if (st == null || !st.isSetFillRect()) {
            return null;
        }
        CTRelativeRect fr = st.getFillRect();
        if (!isMeaningfulCropRect(fr)) {
            return null;
        }
        if (dw < 2 || dh < 2) {
            return null;
        }
        BufferedImage src = decodeRasterBytes(cur);
        if (src == null) {
            return null;
        }
        BufferedImage composed = composeNineSliceStretch(src, dw, dh, fr);
        if (composed == null) {
            return null;
        }
        byte[] png = encodePngOrNull(composed);
        return png;
    }

    private static BufferedImage decodeRasterBytes(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return null;
        }
        try (ByteArrayInputStream bin = new ByteArrayInputStream(raw)) {
            return ImageIO.read(bin);
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] encodePngOrNull(BufferedImage img) {
        if (img == null) {
            return null;
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (!ImageIO.write(img, "png", bos)) {
                return null;
            }
            return bos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static BufferedImage resizeUniform(BufferedImage src, int dw, int dh) {
        BufferedImage dst = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, dw, dh, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    /** OOXML fillRect 분할: 모서리/변은 균등 스케일, 중앙 영역 타깃에 맞춤. */
    private static BufferedImage composeNineSliceStretch(BufferedImage src, int dw, int dh, CTRelativeRect fr) {
        final int sw = src.getWidth();
        final int sh = src.getHeight();
        if (sw < 2 || sh < 2) {
            return resizeUniform(src, dw, dh);
        }
        int lp = fr.isSetL() ? stPctCropInt(fr.getL()) : 0;
        int rp = fr.isSetR() ? stPctCropInt(fr.getR()) : 0;
        int tp = fr.isSetT() ? stPctCropInt(fr.getT()) : 0;
        int bp = fr.isSetB() ? stPctCropInt(fr.getB()) : 0;
        if (lp + rp >= 100000 || tp + bp >= 100000) {
            return resizeUniform(src, dw, dh);
        }
        int sl = (int) Math.round(sw * (lp / 100000.0));
        int sr = (int) Math.round(sw * (rp / 100000.0));
        int stTop = (int) Math.round(sh * (tp / 100000.0));
        int sb = (int) Math.round(sh * (bp / 100000.0));

        sl = clampInt(sl, 0, sw - 2);
        sr = clampInt(sr, 0, sw - 2 - sl);
        stTop = clampInt(stTop, 0, sh - 2);
        sb = clampInt(sb, 0, sh - 2 - stTop);

        int midSw = sw - sl - sr;
        int midSh = sh - stTop - sb;
        int dl = (int) Math.round(dw * (lp / 100000.0));
        int drInset = (int) Math.round(dw * (rp / 100000.0));
        int dt = (int) Math.round(dh * (tp / 100000.0));
        int dbInset = (int) Math.round(dh * (bp / 100000.0));

        dl = clampInt(dl, 0, dw - 2);
        drInset = clampInt(drInset, 0, dw - 2 - dl);
        dt = clampInt(dt, 0, dh - 2);
        dbInset = clampInt(dbInset, 0, dh - 2 - dt);

        int dmw = dw - dl - drInset;
        int dmh = dh - dt - dbInset;
        if (midSw < 1 || midSh < 1 || dmw < 1 || dmh < 1) {
            return resizeUniform(src, dw, dh);
        }

        BufferedImage dst = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            final int sxMidStart = sw - sr;
            final int syMidStart = sh - sb;
            final int dxMidStart = dw - drInset;
            final int dyMidStart = dh - dbInset;

            int[] sxLo = {0, sl, sxMidStart};
            int[] sxHi = {sl, sxMidStart, sw};
            int[] syLo = {0, stTop, syMidStart};
            int[] syHi = {stTop, syMidStart, sh};

            int[] dxLo = {0, dl, dxMidStart};
            int[] dxHi = {dl, dxMidStart, dw};
            int[] dyLo = {0, dt, dyMidStart};
            int[] dyHi = {dt, dyMidStart, dh};

            for (int ri = 0; ri < 3; ri++) {
                for (int ci = 0; ci < 3; ci++) {
                    int sxA = sxLo[ci];
                    int sxB = sxHi[ci];
                    int syA = syLo[ri];
                    int syB = syHi[ri];
                    int dxA = dxLo[ci];
                    int dxB = dxHi[ci];
                    int dyA = dyLo[ri];
                    int dyB = dyHi[ri];
                    if (sxB <= sxA || syB <= syA || dxB <= dxA || dyB <= dyA) {
                        continue;
                    }
                    g.drawImage(src, dxA, dyA, dxB, dyB, sxA, syA, sxB, syB, null);
                }
            }
        } finally {
            g.dispose();
        }
        return dst;
    }

    /** `p:sp` / 연결선 `p:cxnSp` 의 `spPr`. */
    private static CTShapeProperties resolveShapeSpPr(XmlObject xo) {
        if (xo instanceof CTShape) {
            return ((CTShape) xo).getSpPr();
        }
        if (xo instanceof CTConnector) {
            return ((CTConnector) xo).getSpPr();
        }
        return null;
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
        if (shape instanceof XSLFTable) {
            return tableShapeToDto((XSLFTable) shape, interiorPt, outerPt);
        }
        if (shape instanceof XSLFPictureShape) {
            return pictureShapeToDto((XSLFPictureShape) shape, interiorPt, outerPt);
        }
        if (shape instanceof XSLFGraphicFrame) {
            XSLFGraphicFrame gf = (XSLFGraphicFrame) shape;
            if (gf.hasChart() || gf.hasDiagram()) {
                return null;
            }
            XSLFPictureShape fb = gf.getFallbackPicture();
            if (fb != null) {
                return pictureShapeToDto(fb, interiorPt, outerPt);
            }
            return null;
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

    /** PowerPoint 표({@link XSLFTable}) → {@code type: TABLE} + {@link TableDataDTO}(네이티브 표·폴백 공용). */
    private ShapeDTO tableShapeToDto(XSLFTable table, Rectangle2D interiorPt, Rectangle2D outerPt) {
        try {
            table.updateCellAnchor();
        } catch (Exception e) {
            // 무시 — 일부 문서에서만 유효
        }
        Rectangle2D tAnchor = table.getAnchor();
        double tableWPt = Math.max(tAnchor.getWidth(), 1e-6);
        double tableHPt = Math.max(tAnchor.getHeight(), 1e-6);

        ShapeDTO shapeDTO = new ShapeDTO();
        shapeDTO.setType("TABLE");
        applyAnchorToDto(shapeDTO, tAnchor, interiorPt, outerPt);
        shapeDTO.setRotation(table.getRotation());
        shapeDTO.setFlipHorizontal(table.getFlipHorizontal());
        shapeDTO.setFlipVertical(table.getFlipVertical());

        int rows = table.getNumberOfRows();
        int cols = table.getNumberOfColumns();
        double[] colEdgesPt = buildTableColumnEdgesPt(table, cols, tableWPt);
        double[] rowEdgesPt = buildTableRowEdgesPt(table, rows, tableHPt);

        TableDataDTO tableData = new TableDataDTO();
        tableData.setNumRows(rows);
        tableData.setNumColumns(cols);
        boolean merged = false;
        for (int j = 0; j < cols; j++) {
            tableData.getColumnWidthsPx().add(toPx(colEdgesPt[j + 1] - colEdgesPt[j]));
        }
        for (int i = 0; i < rows; i++) {
            tableData.getRowHeightsPx().add(toPx(rowEdgesPt[i + 1] - rowEdgesPt[i]));
        }

        List<TableCellPieceDTO> pieces = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                XSLFTableCell cell;
                try {
                    cell = table.getCell(r, c);
                } catch (Exception e) {
                    continue;
                }
                if (cell == null || cell.isMerged()) {
                    continue;
                }
                int gs = Math.max(1, cell.getGridSpan());
                int rs = Math.max(1, cell.getRowSpan());
                gs = Math.min(gs, cols - c);
                rs = Math.min(rs, rows - r);
                if (gs < 1 || rs < 1) {
                    continue;
                }
                if (gs > 1 || rs > 1) {
                    merged = true;
                }

                double xPt = colEdgesPt[c];
                double yPt = rowEdgesPt[r];
                double cellWPt = colEdgesPt[c + gs] - colEdgesPt[c];
                double cellHPt = rowEdgesPt[r + rs] - rowEdgesPt[r];
                if (cellWPt <= 1e-9 || cellHPt <= 1e-9) {
                    continue;
                }

                TableCellPieceDTO piece = new TableCellPieceDTO();
                piece.setRow(r);
                piece.setCol(c);
                piece.setRowSpan(rs);
                piece.setColSpan(gs);
                piece.setX(toPx(xPt));
                piece.setY(toPx(yPt));
                piece.setWidth(toPx(cellWPt));
                piece.setHeight(toPx(cellHPt));

                Color fillCol = cell.getFillColor();
                if (fillCol != null) {
                    piece.setFillHex(String.format("#%02x%02x%02x",
                        fillCol.getRed(), fillCol.getGreen(), fillCol.getBlue()));
                }
                applyTableCellStrokeFromBorders(cell, piece);

                TextDTO txt = extractText(cell);
                if (txt != null) {
                    piece.setText(txt);
                    maybeLightenTextOnVeryDarkTableCell(piece.getFillHex(), txt);
                }
                pieces.add(piece);
            }
        }
        tableData.setMergedCells(merged);
        tableData.setPieces(pieces);
        if (pieces.isEmpty()) {
            return null;
        }
        shapeDTO.setTable(tableData);
        return shapeDTO;
    }

    /** tblGrid 컬럼 폭 합계를 표 앵커 폭(pt)에 맞게 스케일한 누적 경계(길이 cols+1). */
    private static double[] buildTableColumnEdgesPt(XSLFTable table, int cols, double tableWidthPt) {
        double[] raw = new double[cols];
        double sum = 0;
        for (int j = 0; j < cols; j++) {
            double w = safePositiveOrZero(table.getColumnWidth(j));
            raw[j] = w;
            sum += w;
        }
        return buildDistributedEdges(raw, cols, Math.max(tableWidthPt, 1e-9));
    }

    private static double[] buildTableRowEdgesPt(XSLFTable table, int rows, double tableHeightPt) {
        double[] raw = new double[rows];
        double sum = 0;
        for (int i = 0; i < rows; i++) {
            double h = safePositiveOrZero(table.getRowHeight(i));
            raw[i] = h;
            sum += h;
        }
        return buildDistributedEdges(raw, rows, Math.max(tableHeightPt, 1e-9));
    }

    /** raw 합이 0이면 균등 분배, 아니면 totalLen 비율로 스케일 후 끝점을 totalLen에 스냅. */
    private static double[] buildDistributedEdges(double[] raw, int n, double totalLen) {
        double[] edges = new double[n + 1];
        edges[0] = 0;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += Math.max(0, raw[i]);
        }
        if (sum < 1e-9) {
            double eq = totalLen / Math.max(1, n);
            for (int i = 0; i < n; i++) {
                edges[i + 1] = edges[i] + eq;
            }
        } else {
            double scale = totalLen / sum;
            for (int i = 0; i < n; i++) {
                edges[i + 1] = edges[i] + Math.max(0, raw[i]) * scale;
            }
        }
        edges[n] = totalLen;
        return edges;
    }

    private static double safePositiveOrZero(double v) {
        if (!Double.isFinite(v)) {
            return 0;
        }
        return v > 1e-12 ? v : 0;
    }

    /** 셀 네 방향 테두리 중 하나로 사각형 stroke 설정(없으면 연회색 얇은 선). */
    private void applyTableCellStrokeFromBorders(XSLFTableCell cell, ShapeDTO dto) {
        Color strokeCol = null;
        double maxWPt = 0;
        for (TableCell.BorderEdge edge : TableCell.BorderEdge.values()) {
            try {
                Color col = cell.getBorderColor(edge);
                if (col != null && strokeCol == null) {
                    strokeCol = col;
                }
                Double bw = cell.getBorderWidth(edge);
                if (bw != null && bw > maxWPt) {
                    maxWPt = bw;
                }
            } catch (Exception e) {
                // 무시
            }
        }
        if (strokeCol != null) {
            dto.setStrokeHex(String.format("#%02x%02x%02x",
                strokeCol.getRed(), strokeCol.getGreen(), strokeCol.getBlue()));
            dto.setStrokeWeight(maxWPt > 0 ? Math.max(1, toPx(maxWPt)) : 1);
        } else {
            dto.setStrokeHex("#000000");
            dto.setStrokeWeight(1);
        }
    }

    private void applyTableCellStrokeFromBorders(XSLFTableCell cell, TableCellPieceDTO piece) {
        ShapeDTO tmp = new ShapeDTO();
        applyTableCellStrokeFromBorders(cell, tmp);
        piece.setStrokeHex(tmp.getStrokeHex());
        piece.setStrokeWeight(tmp.getStrokeWeight());
    }

    /**
     * 배경이 매우 어두운데(예: 검은 헤더 바) 글자색도 어둡게 풀리면 피그마에서 안 보임.
     * 명시적으로 밝은 글색이 하나도 없을 때만 런을 흰색으로 바꿈.
     */
    private void maybeLightenTextOnVeryDarkFill(ShapeDTO shapeDTO) {
        maybeLightenTextOnVeryDarkTableCell(shapeDTO.getFillHex(), shapeDTO.getText());
    }

    private void maybeLightenTextOnVeryDarkTableCell(String fillHex, TextDTO txt) {
        if (fillHex == null || !isVeryDarkRgbHex(fillHex)) {
            return;
        }
        if (txt == null) {
            return;
        }
        boolean hasBrightRun = false;
        for (TextParagraphDTO p : txt.getParagraphs()) {
            for (TextRunDTO run : p.getRuns()) {
                String hex = run.getColorHex();
                if (hex != null && rgbHexLuminance(hex) > 175) {
                    hasBrightRun = true;
                    break;
                }
            }
        }
        if (hasBrightRun) {
            return;
        }
        for (TextParagraphDTO p : txt.getParagraphs()) {
            for (TextRunDTO run : p.getRuns()) {
                String hex = run.getColorHex();
                if (hex == null || rgbHexLuminance(hex) < 96) {
                    run.setColorHex("#FFFFFF");
                }
            }
        }
        TextParagraphDTO firstP = txt.getParagraphs().get(0);
        if (firstP != null && !firstP.getRuns().isEmpty()) {
            txt.setColorHex(firstP.getRuns().get(0).getColorHex());
        }
    }

    private static boolean isVeryDarkRgbHex(String hex7) {
        return rgbHexLuminance(hex7) < 48;
    }

    /** 0–255 perceptual grayscale luminance */
    private static int rgbHexLuminance(String hex) {
        try {
            if (hex == null || hex.length() < 7 || !hex.startsWith("#")) {
                return 256;
            }
            int r = Integer.parseUnsignedInt(hex.substring(1, 3), 16);
            int g = Integer.parseUnsignedInt(hex.substring(3, 5), 16);
            int b = Integer.parseUnsignedInt(hex.substring(5, 7), 16);
            return (r * 299 + g * 587 + b * 114) / 1000;
        } catch (Exception e) {
            return 256;
        }
    }

    private static int clampCropThousandths(int v) {
        if (v < 0) {
            return 0;
        }
        return Math.min(v, 100000);
    }

    private static int clampInt(int v, int lo, int hi) {
        if (hi < lo) {
            return lo;
        }
        return Math.max(lo, Math.min(hi, v));
    }

    /** OOXML {@code ST_Percentage} 오브젝트 → 정수 크롭 백분(100000 분율 예상). */
    private static int stPctCropInt(java.lang.Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number) {
            return clampCropThousandths(((Number) o).intValue());
        }
        try {
            return clampCropThousandths(Integer.parseInt(o.toString().trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** srcRect 속성 하나라도 양수이면 크롭으로 간주 */
    private static boolean isMeaningfulCropRect(CTRelativeRect rr) {
        int l = rr.isSetL() ? stPctCropInt(rr.getL()) : 0;
        int r = rr.isSetR() ? stPctCropInt(rr.getR()) : 0;
        int t = rr.isSetT() ? stPctCropInt(rr.getT()) : 0;
        int bottom = rr.isSetB() ? stPctCropInt(rr.getB()) : 0;
        return l > 0 || r > 0 || t > 0 || bottom > 0;
    }

    /** ImageIO 가능한 래스터에만 적용; EMF 등은 원본 바이트 그대로. 성공 시 항상 PNG 바이트(새 배열). */
    private static byte[] applySrcRectCropToRaster(byte[] raw, CTRelativeRect rr) {
        if (raw == null || raw.length == 0 || rr == null || !isMeaningfulCropRect(rr)) {
            return raw;
        }
        int l = rr.isSetL() ? stPctCropInt(rr.getL()) : 0;
        int rSide = rr.isSetR() ? stPctCropInt(rr.getR()) : 0;
        int t = rr.isSetT() ? stPctCropInt(rr.getT()) : 0;
        int uBottom = rr.isSetB() ? stPctCropInt(rr.getB()) : 0;
        if (l + rSide >= 100000 || t + uBottom >= 100000) {
            return raw;
        }
        BufferedImage src;
        try (ByteArrayInputStream bin = new ByteArrayInputStream(raw)) {
            src = ImageIO.read(bin);
        } catch (IOException e) {
            return raw;
        }
        if (src == null || src.getWidth() < 2 || src.getHeight() < 2) {
            return raw;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        int x0 = (int) Math.round(w * (l / 100000.0));
        int y0 = (int) Math.round(h * (t / 100000.0));
        int cropW = (int) Math.round(w * ((100000 - l - rSide) / 100000.0));
        int cropH = (int) Math.round(h * ((100000 - t - uBottom) / 100000.0));
        cropW = Math.max(1, Math.min(cropW, w - Math.min(x0, w - 1)));
        cropH = Math.max(1, Math.min(cropH, h - Math.min(y0, h - 1)));
        BufferedImage dst = new BufferedImage(cropW, cropH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.drawImage(src, 0, 0, cropW, cropH, x0, y0, x0 + cropW, y0 + cropH, null);
        } finally {
            g.dispose();
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (!ImageIO.write(dst, "png", bos)) {
                return raw;
            }
            return bos.toByteArray();
        } catch (IOException e) {
            return raw;
        }
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
                    CTBlipFillProperties bf = readBlipFillFromPicture(picture);
                    int dwp = Math.max(1, (int) Math.round(shapeDTO.getWidth()));
                    int dhp = Math.max(1, (int) Math.round(shapeDTO.getHeight()));
                    BlipPipelineOutcome pipe = processBlipPipeline(raw, bf, dwp, dhp, mime);
                    putImagePayload(shapeDTO, pipe.bytes, pipe.mime, pipe.tileStyle);
                }
            }
        } catch (Exception ignored) {
        }

        return shapeDTO;
    }

    /** 원시 바이트·MIME을 ShapeDTO에 넣는다 (상한 초과 시 Base64 생략). */
    private void putImagePayload(
            ShapeDTO shapeDTO,
            byte[] raw,
            String mimeHint,
            ImageFillStyleDTO imageFillStyle) {
        if (raw == null || raw.length == 0) {
            return;
        }
        String mime = (mimeHint != null && !mimeHint.isEmpty()) ? mimeHint : "image/png";
        shapeDTO.setImageMimeType(mime);
        shapeDTO.setImageFillStyle(null);
        if (raw.length <= MAX_IMAGE_BYTES) {
            shapeDTO.setImageBase64(Base64.getEncoder().encodeToString(raw));
            shapeDTO.setImageFillStyle(imageFillStyle);
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
                String m = (mime != null && !mime.isEmpty()) ? mime : "image/png";
                CTBlipFillProperties bf = readBlipFillFromSimpleShape(simpleShape);
                int dwp = Math.max(1, (int) Math.round(shapeDTO.getWidth()));
                int dhp = Math.max(1, (int) Math.round(shapeDTO.getHeight()));
                BlipPipelineOutcome pipe = processBlipPipeline(raw, bf, dwp, dhp, m);
                putImagePayload(shapeDTO, pipe.bytes, pipe.mime, pipe.tileStyle);
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
                maybeLightenTextOnVeryDarkFill(shapeDTO);
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

        XSLFSheet sheet = textShape.getSheet();
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
            XmlObject pxo = paragraph.getXmlObject();
            boolean paraHasAny;
            if (pxo instanceof CTTextParagraph) {
                paraHasAny = appendRunsFromDrawingParagraph((CTTextParagraph) pxo, paragraphDTO,
                    paragraphText, sheet);
            } else {
                paraHasAny = appendRunsFromPoiParagraph(paragraph, paragraphDTO, paragraphText, sheet);
            }
            if (pxo instanceof CTTextParagraph) {
                applyParagraphAlignFromCt((CTTextParagraph) pxo, paragraphDTO);
            }
            if (paraHasAny) {
                hasAnyText = true;
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
        applyTextBodyInsetsPx(textShape, textDTO);
        return textDTO;
    }

    /**
     * OOXML 순서 유지로 {@code a:r}, {@code a:fld}(플레이스홀더/힌트), {@code a:br} 를 읽는다.
     * POI {@code paragraph.getTextRuns()} 는 {@code fld} 텍스트를 빠뜨리는 경우가 있다.
     */
    private boolean appendRunsFromDrawingParagraph(
        CTTextParagraph ctp,
        TextParagraphDTO paragraphDTO,
        StringBuilder paragraphSb,
        XSLFSheet sheet) {
        boolean any = false;
        XmlCursor cur = ctp.newCursor();
        try {
            if (!cur.toFirstChild()) {
                return false;
            }
            do {
                XmlObject obj = cur.getObject();
                if (obj instanceof CTRegularTextRun) {
                    CTRegularTextRun r = (CTRegularTextRun) obj;
                    String t = r.getT();
                    if (t == null) {
                        t = "";
                    }
                    TextRunDTO runDTO = new TextRunDTO();
                    runDTO.setText(t);
                    applyCharPropsToDto(r.isSetRPr() ? r.getRPr() : null, sheet, runDTO);
                    paragraphDTO.getRuns().add(runDTO);
                    paragraphSb.append(t);
                    if (!t.isEmpty()) {
                        any = true;
                    }
                } else if (obj instanceof CTTextField) {
                    CTTextField f = (CTTextField) obj;
                    String t = f.isSetT() ? f.getT() : "";
                    TextRunDTO runDTO = new TextRunDTO();
                    runDTO.setText(t);
                    applyCharPropsToDto(f.isSetRPr() ? f.getRPr() : null, sheet, runDTO);
                    paragraphDTO.getRuns().add(runDTO);
                    paragraphSb.append(t);
                    if (!t.isEmpty()) {
                        any = true;
                    }
                } else if (obj instanceof CTTextLineBreak) {
                    TextRunDTO runDTO = new TextRunDTO();
                    runDTO.setText("\n");
                    paragraphDTO.getRuns().add(runDTO);
                    paragraphSb.append('\n');
                    any = true;
                }
            } while (cur.toNextSibling());
        } finally {
            cur.dispose();
        }
        return any;
    }

    /**
     * OOXML {@code a:p/a:pPr/@algn} — 단락 가로 정렬(CTR=가운데 등). POI 값을 덮어쓴다.
     */
    private static void applyParagraphAlignFromCt(CTTextParagraph ctp, TextParagraphDTO dto) {
        if (!ctp.isSetPPr() || !ctp.getPPr().isSetAlgn()) {
            return;
        }
        String a = mapStTextAlignTypeForDto(ctp.getPPr().getAlgn());
        if (a != null) {
            dto.setAlign(a);
        }
    }

    private static String mapStTextAlignTypeForDto(STTextAlignType.Enum a) {
        if (a == null) {
            return null;
        }
        if (STTextAlignType.L.equals(a)) {
            return "LEFT";
        }
        if (STTextAlignType.CTR.equals(a)) {
            return "CENTER";
        }
        if (STTextAlignType.R.equals(a)) {
            return "RIGHT";
        }
        if (STTextAlignType.JUST.equals(a) || STTextAlignType.JUST_LOW.equals(a)
            || STTextAlignType.DIST.equals(a) || STTextAlignType.THAI_DIST.equals(a)) {
            return "JUSTIFY";
        }
        return "LEFT";
    }

    private static String pptVerticalAnchorDtoValue(STTextAnchoringType.Enum anchor) {
        if (anchor == null) {
            return null;
        }
        if (STTextAnchoringType.T.equals(anchor)) {
            return "TOP";
        }
        if (STTextAnchoringType.CTR.equals(anchor)) {
            return "CTR";
        }
        if (STTextAnchoringType.B.equals(anchor)) {
            return "BOT";
        }
        if (STTextAnchoringType.JUST.equals(anchor)) {
            return "JUST";
        }
        if (STTextAnchoringType.DIST.equals(anchor)) {
            return "DIST";
        }
        return null;
    }

    /** CT 단락이 없거나 예외적일 때만 — 기존 POI 런 순회 */
    private boolean appendRunsFromPoiParagraph(
        XSLFTextParagraph paragraph,
        TextParagraphDTO paragraphDTO,
        StringBuilder paragraphSb,
        XSLFSheet sheet) {
        boolean any = false;
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
            runDTO.setColorHex(colorHexFromPaintOrRunXml(run, sheet));

            paragraphDTO.getRuns().add(runDTO);
            paragraphSb.append(runText);
            if (!runText.isEmpty()) {
                any = true;
            }
        }
        return any;
    }

    private void applyCharPropsToDto(
        CTTextCharacterProperties rPr,
        XSLFSheet sheet,
        TextRunDTO dto) {
        if (rPr == null) {
            return;
        }
        try {
            String family = null;
            if (rPr.isSetEa()) {
                family = trimToNull(rPr.getEa().getTypeface());
            }
            if (family == null && rPr.isSetLatin()) {
                family = trimToNull(rPr.getLatin().getTypeface());
            }
            if (family != null) {
                dto.setFontFamily(family);
            }
            if (rPr.isSetSz()) {
                dto.setFontSize(toPx(rPr.getSz() / 100.0));
            }
            if (rPr.isSetB()) {
                dto.setBold(rPr.getB());
            }
            if (rPr.isSetI()) {
                dto.setItalic(rPr.getI());
            }
            if (rPr.isSetU()) {
                STTextUnderlineType.Enum u = rPr.getU();
                dto.setUnderline(u != null && !STTextUnderlineType.NONE.equals(u));
            }
            dto.setColorHex(colorHexFromCharProps(rPr, sheet));
        } catch (Exception e) {
            // 무시 — POI 폴백 런은 별도 경로에서 색을 채운다.
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static double emuToPx(long emu) {
        return toPx(Units.toPoints(emu));
    }

    /** PPT 도형 {@code p:sp/p:txBody/a:bodyPr} inset → px (Figma 텍스트 박스 안쪽 여백). */
    private void applyTextBodyInsetsPx(XSLFTextShape textShape, TextDTO textDTO) {
        try {
            CTTextBody txBody = findTxBody(textShape.getXmlObject());
            if (txBody == null || txBody.getBodyPr() == null) {
                return;
            }
            CTTextBodyProperties bp = txBody.getBodyPr();
            boolean anySideIns = bp.isSetLIns() || bp.isSetTIns() || bp.isSetRIns() || bp.isSetBIns();
            if (!anySideIns) {
                /* XML 에 inset 속성이 하나도 없을 때 PP 가 쓰는 것과 비슷한 기본값(약 0.05in) */
                long defEmu = 45720L;
                textDTO.setInsetLeft(emuToPx(defEmu));
                textDTO.setInsetTop(emuToPx(defEmu));
                textDTO.setInsetRight(emuToPx(defEmu));
                textDTO.setInsetBottom(emuToPx(defEmu));
            }
            if (bp.isSetLIns()) {
                textDTO.setInsetLeft(emuToPx(coordAttrToLong(bp.getLIns())));
            }
            if (bp.isSetTIns()) {
                textDTO.setInsetTop(emuToPx(coordAttrToLong(bp.getTIns())));
            }
            if (bp.isSetRIns()) {
                textDTO.setInsetRight(emuToPx(coordAttrToLong(bp.getRIns())));
            }
            if (bp.isSetBIns()) {
                textDTO.setInsetBottom(emuToPx(coordAttrToLong(bp.getBIns())));
            }
            if (bp.isSetAnchor()) {
                String va = pptVerticalAnchorDtoValue(bp.getAnchor());
                if (va != null) {
                    textDTO.setVerticalAlign(va);
                }
            }
        } catch (Exception e) {
            // 무시
        }
    }

    private static CTTextBody findTxBody(XmlObject xo) {
        if (xo instanceof CTShape) {
            CTShape cs = (CTShape) xo;
            return cs.isSetTxBody() ? cs.getTxBody() : null;
        }
        return null;
    }

    private static long coordAttrToLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        String s = v.toString().trim();
        if (s.isEmpty()) {
            return 0L;
        }
        return Long.parseLong(s);
    }

    private static String colorHexFromPaintOrRunXml(XSLFTextRun run, XSLFSheet sheet) {
        PaintStyle fontPaint = run.getFontColor();
        if (fontPaint instanceof PaintStyle.SolidPaint) {
            Color textColor = ((PaintStyle.SolidPaint) fontPaint).getSolidColor().getColor();
            if (textColor != null) {
                return String.format("#%02x%02x%02x",
                    textColor.getRed(), textColor.getGreen(), textColor.getBlue());
            }
        }
        return colorHexFromRunDrawingXml(run, sheet);
    }

    /** {@code a:rPr/a:solidFill} (srgbClr·schemeClr + lumMod). */
    private static String colorHexFromCharProps(CTTextCharacterProperties rPr, XSLFSheet sheet) {
        try {
            if (rPr == null || !rPr.isSetSolidFill()) {
                return null;
            }
            CTSolidColorFillProperties sf = rPr.getSolidFill();
            if (sf.isSetSrgbClr()) {
                byte[] rgb = sf.getSrgbClr().getVal();
                if (rgb != null && rgb.length >= 3) {
                    return String.format("#%02x%02x%02x",
                        rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
                }
            }
            if (sf.isSetSchemeClr()) {
                CTSchemeColor sc = sf.getSchemeClr();
                Color base = resolveSchemeColor(sheet, sc.getVal() == null ? null : sc.getVal().toString());
                if (base != null) {
                    int mod = combinedLumModPercent(sc);
                    if (mod >= 0 && mod != 100) {
                        base = scaleRgbByPercent(base, mod);
                    }
                    return String.format("#%02x%02x%02x",
                        base.getRed(), base.getGreen(), base.getBlue());
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * POI가 테마/placeholder 색을 {@link PaintStyle}으로 풀지 못한 경우,
     * {@code a:r/a:rPr/a:solidFill} 을 직접 읽는다.
     */
    private static String colorHexFromRunDrawingXml(XSLFTextRun run, XSLFSheet sheet) {
        try {
            XmlObject runXml = run.getXmlObject();
            if (!(runXml instanceof CTRegularTextRun)) {
                return null;
            }
            CTRegularTextRun ctr = (CTRegularTextRun) runXml;
            if (!ctr.isSetRPr()) {
                return null;
            }
            return colorHexFromCharProps(ctr.getRPr(), sheet);
        } catch (Exception e) {
            return null;
        }
    }

    /** schemeClr 자식 lumMod 등을 간단히 합성한 백분율(대략 0–400+). 미적용 시 -1. */
    private static int combinedLumModPercent(CTSchemeColor sc) {
        int p = -1;
        if (sc == null) {
            return -1;
        }
        try {
            for (org.openxmlformats.schemas.drawingml.x2006.main.CTPercentage lum : sc.getLumModList()) {
                Object raw = lum.getVal();
                int v = raw instanceof Number ? ((Number) raw).intValue() : Integer.parseInt(raw.toString());
                if (v <= 0) {
                    continue;
                }
                if (p < 0) {
                    p = 100;
                }
                p = Math.max(1, Math.min(400_000, p * v / 100_000));
            }
        } catch (Exception e) {
            return -1;
        }
        return p;
    }

    private static Color scaleRgbByPercent(Color base, int percent) {
        double f = Math.max(0, Math.min(4.0, percent / 100.0));
        int r = (int) Math.round(Math.max(0, Math.min(255, base.getRed() * f)));
        int g = (int) Math.round(Math.max(0, Math.min(255, base.getGreen() * f)));
        int b = (int) Math.round(Math.max(0, Math.min(255, base.getBlue() * f)));
        return new Color(r, g, b);
    }

    private static Color resolveSchemeColor(XSLFSheet sheet, String schemeName) {
        if (sheet == null || schemeName == null) {
            return null;
        }
        XSLFTheme theme = sheet.getTheme();
        if (theme == null) {
            return null;
        }
        CTOfficeStyleSheet os = theme.getXmlObject();
        if (os == null || os.getThemeElements() == null || os.getThemeElements().getClrScheme() == null) {
            return null;
        }
        CTColorScheme sch = os.getThemeElements().getClrScheme();
        CTColor entry = themeSwatch(sch, schemeName.trim());
        return colorFromCtColor(entry);
    }

    private static CTColor themeSwatch(CTColorScheme sch, String raw) {
        if (sch == null || raw == null) {
            return null;
        }
        String n = raw.toLowerCase(Locale.ROOT);
        switch (n) {
            case "dk1":
                return sch.getDk1();
            case "lt1":
                return sch.getLt1();
            case "dk2":
                return sch.getDk2();
            case "lt2":
                return sch.getLt2();
            case "accent1":
                return sch.getAccent1();
            case "accent2":
                return sch.getAccent2();
            case "accent3":
                return sch.getAccent3();
            case "accent4":
                return sch.getAccent4();
            case "accent5":
                return sch.getAccent5();
            case "accent6":
                return sch.getAccent6();
            case "hlink":
                return sch.getHlink();
            case "folhlink":
            case "fol_hlink":
                return sch.getFolHlink();
            // OOXML clrScheme 에는 dk/lt/accent 등만 있다. Office 의 tx/bg/ph 는 일반 매핑을 따름.
            case "tx1":
                return sch.getDk1();
            case "tx2":
                return sch.getDk2();
            case "bg1":
                return sch.getLt1();
            case "bg2":
                return sch.getLt2();
            case "phclr":
            case "ph_clr":
                return sch.getLt2();
            default:
                return null;
        }
    }

    private static Color colorFromCtColor(CTColor c) {
        if (c == null) {
            return null;
        }
        if (c.isSetSrgbClr()) {
            byte[] b = c.getSrgbClr().getVal();
            if (b != null && b.length >= 3) {
                return new Color(b[0] & 0xFF, b[1] & 0xFF, b[2] & 0xFF);
            }
        }
        if (c.isSetSysClr()) {
            Object last = c.getSysClr().getLastClr();
            if (last instanceof byte[]) {
                byte[] b = (byte[]) last;
                if (b.length >= 3) {
                    return new Color(b[0] & 0xFF, b[1] & 0xFF, b[2] & 0xFF);
                }
            }
            try {
                String hex = last != null ? last.toString().trim() : "";
                if (hex.length() >= 6) {
                    return new Color(
                        Integer.parseUnsignedInt(hex.substring(0, 2), 16),
                        Integer.parseUnsignedInt(hex.substring(2, 4), 16),
                        Integer.parseUnsignedInt(hex.substring(4, 6), 16));
                }
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}
