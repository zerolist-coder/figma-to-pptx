package com.ppttofigma.backend.controller;

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

/**
 * PPTX 가져오기 REST 진입점.
 * <p><b>역할:</b> 멀티파트 업로드 수신, {@link com.ppttofigma.backend.service.PptxService#analyzePptx} 호출,
 * 결과·에러를 JSON 맵으로 감싸 반환한다({@code data}, {@code error} 등).
 * <p><b>기능:</b> CORS 허용, 최소 로그(파일명).
 */
@RestController
@RequestMapping("/api/imports")
@CrossOrigin(origins = "*")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    @Autowired
    private PptxService pptxService;

    /** pptx 파일 한 개를 분석해 JSON으로 돌려준다. 본문 필드 {@code file}. */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzePptx(@RequestParam("file") MultipartFile file) {
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "(이름 없음)";
        Map<String, Object> response = new HashMap<>();

        if (file.isEmpty()) {
            log.warn("{}", fileName);
            response.put("error", "File is empty");
            return ResponseEntity.badRequest().body(response);
        }

        log.info("{}", fileName);

        try {
            PptxDataDTO analyzedData = pptxService.analyzePptx(file);

            response.put("importId", "imp_" + System.currentTimeMillis());
            response.put("message", "Analysis complete");
            response.put("data", analyzedData);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("{}", fileName, e);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
