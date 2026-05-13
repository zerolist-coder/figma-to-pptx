package com.ppttofigma.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class TextDTO {
    // 호환을 위한 단순 필드 (paragraphs[0].runs[0] 기준 채움)
    private String content;
    private double fontSize;
    private String colorHex;
    private String align; // LEFT, CENTER, RIGHT, JUSTIFY

    // 멀티 단락 / 멀티 run 정보
    private List<TextParagraphDTO> paragraphs = new ArrayList<>();

    /**
     * OOXML {@code a:bodyPr} anchor — 세로 기준점. 플러그인: CTR → 텍스트세로가운데.
     * 값 예: TOP(T), CTR, BOT(B), JUST, DIST
     */
    private String verticalAlign;
    private Double insetLeft;
    private Double insetTop;
    private Double insetRight;
    private Double insetBottom;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public double getFontSize() { return fontSize; }
    public void setFontSize(double fontSize) { this.fontSize = fontSize; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public String getAlign() { return align; }
    public void setAlign(String align) { this.align = align; }
    public List<TextParagraphDTO> getParagraphs() { return paragraphs; }
    public void setParagraphs(List<TextParagraphDTO> paragraphs) { this.paragraphs = paragraphs; }
    public Double getInsetLeft() { return insetLeft; }
    public void setInsetLeft(Double insetLeft) { this.insetLeft = insetLeft; }
    public Double getInsetTop() { return insetTop; }
    public void setInsetTop(Double insetTop) { this.insetTop = insetTop; }
    public Double getInsetRight() { return insetRight; }
    public void setInsetRight(Double insetRight) { this.insetRight = insetRight; }
    public Double getInsetBottom() { return insetBottom; }
    public void setInsetBottom(Double insetBottom) { this.insetBottom = insetBottom; }
    public String getVerticalAlign() { return verticalAlign; }
    public void setVerticalAlign(String verticalAlign) { this.verticalAlign = verticalAlign; }
}
