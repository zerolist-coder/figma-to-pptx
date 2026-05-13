package com.ppttofigma.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class SlideDTO {
    private int slideNumber;
    /** 마스터/레이아웃 매핑 실패 시 -1 */
    private int masterIndex = -1;
    private int layoutIndex = -1;
    /** 슬라이드 배경 단색(상속 해석 후). 없으면 null */
    private String backgroundFillHex;
    /** 슬라이드 배경 그림(blip). 레이아웃→마스터 상속 해석 */
    private String backgroundImageBase64;
    private String backgroundImageMimeType;
    private ImageFillStyleDTO backgroundImageFillStyle;
    private List<ShapeDTO> shapes = new ArrayList<>();

    // Getters and Setters
    public int getSlideNumber() { return slideNumber; }
    public void setSlideNumber(int slideNumber) { this.slideNumber = slideNumber; }
    public int getMasterIndex() { return masterIndex; }
    public void setMasterIndex(int masterIndex) { this.masterIndex = masterIndex; }
    public int getLayoutIndex() { return layoutIndex; }
    public void setLayoutIndex(int layoutIndex) { this.layoutIndex = layoutIndex; }
    public String getBackgroundFillHex() { return backgroundFillHex; }
    public void setBackgroundFillHex(String backgroundFillHex) { this.backgroundFillHex = backgroundFillHex; }
    public String getBackgroundImageBase64() { return backgroundImageBase64; }
    public void setBackgroundImageBase64(String backgroundImageBase64) { this.backgroundImageBase64 = backgroundImageBase64; }
    public String getBackgroundImageMimeType() { return backgroundImageMimeType; }
    public void setBackgroundImageMimeType(String backgroundImageMimeType) { this.backgroundImageMimeType = backgroundImageMimeType; }
    public ImageFillStyleDTO getBackgroundImageFillStyle() {
        return backgroundImageFillStyle;
    }
    public void setBackgroundImageFillStyle(ImageFillStyleDTO backgroundImageFillStyle) {
        this.backgroundImageFillStyle = backgroundImageFillStyle;
    }
    public List<ShapeDTO> getShapes() { return shapes; }
    public void setShapes(List<ShapeDTO> shapes) { this.shapes = shapes; }
}
