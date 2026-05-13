package com.ppttofigma.backend.dto;

/**
 * OOXML blip 채우기 중 {@code a:tile} 인 경우 플러그인 {@code ImagePaint} 에 전달할 힌트.
 * stretch+fillRect(9-patch)는 서버에서 타깃 크기 PNG로 합성하므로 이 DTO는 null 이다.
 */
public class ImageFillStyleDTO {
    /** 예: TILE */
    private String scaleMode;
    /** OOXML 타일 sx/sy 를 100000분율로 환산해 Figma {@code ImagePaint.scalingFactor} 에 사용 */
    private Double figmaTileScalingFactor;

    public String getScaleMode() {
        return scaleMode;
    }

    public void setScaleMode(String scaleMode) {
        this.scaleMode = scaleMode;
    }

    public Double getFigmaTileScalingFactor() {
        return figmaTileScalingFactor;
    }

    public void setFigmaTileScalingFactor(Double figmaTileScalingFactor) {
        this.figmaTileScalingFactor = figmaTileScalingFactor;
    }
}
