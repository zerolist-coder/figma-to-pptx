package com.ppttofigma.backend.controller;

import com.ppttofigma.backend.dto.PptxDataDTO;
import com.ppttofigma.backend.service.PptxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/imports")
@CrossOrigin(origins = "*") // Figma 플러그인 환경에서 접근 가능하도록 CORS 허용
public class ImportController {

    @Autowired
    private PptxService pptxService;

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzePptx(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        if (file.isEmpty()) {
            response.put("error", "File is empty");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            System.out.println("====== [파일 분석 시작] ======");
            System.out.println("파일명: " + file.getOriginalFilename());
            
            PptxDataDTO analyzedData = pptxService.analyzePptx(file);
            
            response.put("importId", "imp_" + System.currentTimeMillis());
            response.put("message", "Analysis complete");
            response.put("data", analyzedData);
            
            System.out.println("분석 완료: 슬라이드 " + analyzedData.getSlides().size() + "개 추출됨");
            System.out.println("===============================");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
