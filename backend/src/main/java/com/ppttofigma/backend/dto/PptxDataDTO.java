package com.ppttofigma.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class PptxDataDTO {
    private String fileName;
    private double width;
    private double height;
    private List<SlideDTO> slides = new ArrayList<>();
    private List<MasterDTO> masters = new ArrayList<>();

    // Getters and Setters
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public List<SlideDTO> getSlides() { return slides; }
    public void setSlides(List<SlideDTO> slides) { this.slides = slides; }
    public List<MasterDTO> getMasters() { return masters; }
    public void setMasters(List<MasterDTO> masters) { this.masters = masters; }
}
