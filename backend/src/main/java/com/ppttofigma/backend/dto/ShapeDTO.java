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
    private boolean flipHorizontal;
    private boolean flipVertical;
    private String dashStyle; // SOLID, DASH, DOT 등
    /** POI LineDash.pattern — strokeWeight(px)와 곱해 Figma dashPattern으로 변환 */
    private java.util.List<Integer> dashPatternMultipliers;
    private java.util.List<Double> adjustValues;
    private TextDTO text;
    /**
     * PNG/JPEG 등 원본 바이트 Base64 — 삽입 그림(`PICTURE`) 및 도형 blip 채우기.
     * 삽입 그림에서 OOXML {@code srcRect} 크롭이 있으면 서버에서 래스터 크롭 후 PNG가 될 수 있음.
     */
    private String imageBase64;
    /** 예: image/png, image/jpeg */
    private String imageMimeType;
    /** OOXML tile 등 — stretch+fillRect 는 서버 9-patch 후 null */
    private ImageFillStyleDTO imageFillStyle;
    /** type이 GROUP일 때 자식 도형(슬라이드/부모 좌표와 동일한 로컬 px 규칙) */
    private java.util.List<ShapeDTO> children;
    /** type이 TABLE 일 때 셀 격자·텍스트(네이티브 표 또는 폴백 렌더용) */
    private TableDataDTO table;


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
    public java.util.List<Integer> getDashPatternMultipliers() { return dashPatternMultipliers; }
    public void setDashPatternMultipliers(java.util.List<Integer> dashPatternMultipliers) {
        this.dashPatternMultipliers = dashPatternMultipliers;
    }
    public double getRotation() { return rotation; }
    public void setRotation(double rotation) { this.rotation = rotation; }
    public boolean isFlipHorizontal() {
        return flipHorizontal;
    }
    public void setFlipHorizontal(boolean flipHorizontal) {
        this.flipHorizontal = flipHorizontal;
    }
    public boolean isFlipVertical() {
        return flipVertical;
    }
    public void setFlipVertical(boolean flipVertical) {
        this.flipVertical = flipVertical;
    }
    public java.util.List<Double> getAdjustValues() { return adjustValues; }
    public void setAdjustValues(java.util.List<Double> adjustValues) { this.adjustValues = adjustValues; }
    public TextDTO getText() { return text; }
    public void setText(TextDTO text) { this.text = text; }
    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
    public String getImageMimeType() { return imageMimeType; }
    public void setImageMimeType(String imageMimeType) { this.imageMimeType = imageMimeType; }
    public ImageFillStyleDTO getImageFillStyle() {
        return imageFillStyle;
    }
    public void setImageFillStyle(ImageFillStyleDTO imageFillStyle) {
        this.imageFillStyle = imageFillStyle;
    }
    public java.util.List<ShapeDTO> getChildren() { return children; }
    public void setChildren(java.util.List<ShapeDTO> children) { this.children = children; }
    public TableDataDTO getTable() { return table; }
    public void setTable(TableDataDTO table) { this.table = table; }
}
