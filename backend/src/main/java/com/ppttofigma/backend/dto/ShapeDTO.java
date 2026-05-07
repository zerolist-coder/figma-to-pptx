package com.ppttofigma.backend.dto;

public class ShapeDTO {
    private String type; // RECT, ELLIPSE, TEXT 등
    private double x;
    private double y;
    private double width;
    private double height;
    private String fillHex; // #RRGGBB
    private String strokeHex;
    private double strokeWeight;
    private double rotation;
    private String dashStyle; // SOLID, DASH, DOT 등
    private java.util.List<Double> adjustValues;
    private TextDTO text;

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public String getFillHex() { return fillHex; }
    public void setFillHex(String fillHex) { this.fillHex = fillHex; }
    public String getStrokeHex() { return strokeHex; }
    public void setStrokeHex(String strokeHex) { this.strokeHex = strokeHex; }
    public double getStrokeWeight() { return strokeWeight; }
    public void setStrokeWeight(double strokeWeight) { this.strokeWeight = strokeWeight; }
    public String getDashStyle() { return dashStyle; }
    public void setDashStyle(String dashStyle) { this.dashStyle = dashStyle; }
    public double getRotation() { return rotation; }
    public void setRotation(double rotation) { this.rotation = rotation; }
    public java.util.List<Double> getAdjustValues() { return adjustValues; }
    public void setAdjustValues(java.util.List<Double> adjustValues) { this.adjustValues = adjustValues; }
    public TextDTO getText() { return text; }
    public void setText(TextDTO text) { this.text = text; }
}
