/**
 * UI 폴백용 (서버 응답 전·오프라인). 실제 빌드 태그는 서버 ImportBuildInfo 와 동기화할 것.
 * @see com.ppttofigma.backend.ImportBuildInfo
 */
export const IMPORT_PIPELINE_STEP = 'step1';

export const IMPORT_REVISION = 3;

export function importBuildLabel(): string {
  return `${IMPORT_PIPELINE_STEP} - ${IMPORT_REVISION}`;
}
