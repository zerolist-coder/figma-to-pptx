package com.ppttofigma.backend.dto;

/**
 * 표의 한 덩어리(앵커 셀 + 병합 크기). 좌표는 표 그룹 로컬(px).
 */
public class TableCellPieceDTO {
    private int row;
    private int col;
    private int rowSpan = 1;
    private int colSpan = 1;
    private double x;
    private double y;
    private double width;
    private double height;
    private String fillHex;
    private String strokeHex;
    private Double strokeWeight;
    private TextDTO text;

    public int getRow() { return row; }
    public void setRow(int row) { this.row = row; }
    public int getCol() { return col; }
    public void setCol(int col) { this.col = col; }
    public int getRowSpan() { return rowSpan; }
    public void setRowSpan(int rowSpan) { this.rowSpan = rowSpan; }
    public int getColSpan() { return colSpan; }
    public void setColSpan(int colSpan) { this.colSpan = colSpan; }
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
    public Double getStrokeWeight() { return strokeWeight; }
    public void setStrokeWeight(Double strokeWeight) { this.strokeWeight = strokeWeight; }
    public TextDTO getText() { return text; }
    public void setText(TextDTO text) { this.text = text; }
}
