package com.ppttofigma.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 애플리케이션 부트스트랩.
 * <p><b>역할:</b> 내장 Tomcat 기동, 컴포넌트 스캔({@code com.ppttofigma.backend})으로
 * {@link com.ppttofigma.backend.controller.ImportController}·{@link com.ppttofigma.backend.service.PptxService} 등을 올린다.
 */
@SpringBootApplication
public class PptToFigmaApplication {

	public static void main(String[] args) {
		SpringApplication.run(PptToFigmaApplication.class, args);
	}

}
