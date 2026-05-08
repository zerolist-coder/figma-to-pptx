package com.ppttofigma.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 한 단락(문단)을 표현한다. 단락은 자체 정렬(align)과 여러 run으로 구성된다.
 */
public class TextParagraphDTO {
    private String align; // LEFT, CENTER, RIGHT, JUSTIFY
    private List<TextRunDTO> runs = new ArrayList<>();

    public String getAlign() { return align; }
    public void setAlign(String align) { this.align = align; }
    public List<TextRunDTO> getRuns() { return runs; }
    public void setRuns(List<TextRunDTO> runs) { this.runs = runs; }
}
