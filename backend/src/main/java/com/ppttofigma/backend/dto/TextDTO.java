package com.ppttofigma.backend.dto;

public class TextDTO {
    private String content;
    private double fontSize;
    private String colorHex;
    private String align; // LEFT, CENTER, RIGHT

    // Getters and Setters
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public double getFontSize() { return fontSize; }
    public void setFontSize(double fontSize) { this.fontSize = fontSize; }
    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) { this.colorHex = colorHex; }
    public String getAlign() { return align; }
    public void setAlign(String align) { this.align = align; }
}
