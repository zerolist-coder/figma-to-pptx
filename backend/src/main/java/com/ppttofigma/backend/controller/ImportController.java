package com.ppttofigma.backend.controller;

import com.ppttofigma.backend.ImportBuildInfo;
import com.ppttofigma.backend.dto.PptxDataDTO;
import com.ppttofigma.backend.service.PptxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/imports")
@CrossOrigin(origins = "*")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    @Autowired
    private PptxService pptxService;

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzePptx(@RequestParam("file") MultipartFile file) {
        String tag = ImportBuildInfo.label();
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "(이름 없음)";
        Map<String, Object> response = new HashMap<>();

        if (file.isEmpty()) {
            log.warn("[PPT→Figma {}] {} | (오류) 빈 파일", tag, fileName);
            response.put("error", "File is empty");
            response.put("buildLabel", tag);
            return ResponseEntity.badRequest().body(response);
        }

        log.info("[PPT→Figma {}] {} | 1. 업로드 수신 (크기 {} bytes)", tag, fileName, file.getSize());

        try {
            log.info("[PPT→Figma {}] {} | 2. PptxService.analyzePptx 호출", tag, fileName);
            PptxDataDTO analyzedData = pptxService.analyzePptx(file);

            response.put("importId", "imp_" + System.currentTimeMillis());
            response.put("message", "Analysis complete");
            response.put("data", analyzedData);
            response.put("buildLabel", tag);

            log.info("[PPT→Figma {}] {} | 4. 분석 완료 — 슬라이드 {}장, 캔버스 {}×{} px",
                    tag, fileName, analyzedData.getSlides().size(),
                    analyzedData.getWidth(), analyzedData.getHeight());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[PPT→Figma {}] {} | (오류) {}", tag, fileName, e.getMessage(), e);
            response.put("error", e.getMessage());
            response.put("buildLabel", tag);
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
