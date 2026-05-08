package com.ppttofigma.backend.dto;

/**
 * PPT의 한 단락 안에 있는 단일 스타일 구간(run)을 표현한다.
 * 한 단락에 여러 run이 있으면 한 텍스트 박스 안에서 부분 스타일이 섞인 것이다.
 */
public class TextRunDTO {
    private String text;
    private String fontFamily;
    private double fontSize;
    private String colorHex;
    private boolean bold;
    private boolean italic;
    private boolean underline;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
    public double getFontSize() { return fontSize; }
    public void setFontSize(double fontSize) { this.fontSize = fontSize; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public boolean isBold() { return bold; }
    public void setBold(boolean bold) { this.bold = bold; }
    public boolean isItalic() { return italic; }
    public void setItalic(boolean italic) { this.italic = italic; }
    public boolean isUnderline() { return underline; }
    public void setUnderline(boolean underline) { this.underline = underline; }
}
