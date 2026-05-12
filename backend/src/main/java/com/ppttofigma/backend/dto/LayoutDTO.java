package com.ppttofigma.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class LayoutDTO {
    private String name;
    /** 레이아웃 배경 단색(없으면 마스터 배경으로 폴백한 값). 없으면 null */
    private String backgroundFillHex;
    /** 레이아웃 배경 그림(없으면 마스터 배경 그림으로 폴백한 값) */
    private String backgroundImageBase64;
    private String backgroundImageMimeType;
    private List<ShapeDTO> shapes = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBackgroundFillHex() { return backgroundFillHex; }
    public void setBackgroundFillHex(String backgroundFillHex) { this.backgroundFillHex = backgroundFillHex; }
    public String getBackgroundImageBase64() { return backgroundImageBase64; }
    public void setBackgroundImageBase64(String backgroundImageBase64) { this.backgroundImageBase64 = backgroundImageBase64; }
    public String getBackgroundImageMimeType() { return backgroundImageMimeType; }
    public void setBackgroundImageMimeType(String backgroundImageMimeType) { this.backgroundImageMimeType = backgroundImageMimeType; }
    public List<ShapeDTO> getShapes() { return shapes; }
    public void setShapes(List<ShapeDTO> shapes) { this.shapes = shapes; }
}
