package com.ppttofigma.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class MasterDTO {
    private String name;
    /** 마스터 시트 배경 단색. 없으면 null */
    private String backgroundFillHex;
    private String backgroundImageBase64;
    private String backgroundImageMimeType;
    private List<ShapeDTO> shapes = new ArrayList<>();
    private List<LayoutDTO> layouts = new ArrayList<>();

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
    public List<LayoutDTO> getLayouts() { return layouts; }
    public void setLayouts(List<LayoutDTO> layouts) { this.layouts = layouts; }
}
