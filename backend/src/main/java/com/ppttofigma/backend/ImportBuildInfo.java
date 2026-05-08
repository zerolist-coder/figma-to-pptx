package com.ppttofigma.backend;

/** 플러그인과 동일한 빌드 태그. 수정 시 {@link #REVISION}만 증가 (Eclipse 콘솔 로그용). */
public final class ImportBuildInfo {

    private ImportBuildInfo() {}

    public static final String PIPELINE_STEP = "step1";
    public static final int REVISION = 3;

    public static String label() {
        return PIPELINE_STEP + " - " + REVISION;
    }
}
