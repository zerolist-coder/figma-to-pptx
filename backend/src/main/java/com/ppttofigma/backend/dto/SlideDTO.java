package com.ppttofigma.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class SlideDTO {
    private int slideNumber;
    private List<ShapeDTO> shapes = new ArrayList<>();

    // Getters and Setters
    public int getSlideNumber() { return slideNumber; }
    public void setSlideNumber(int slideNumber) { this.slideNumber = slideNumber; }
    public List<ShapeDTO> getShapes() { return shapes; }
    public void setShapes(List<ShapeDTO> shapes) { this.shapes = shapes; }
}
