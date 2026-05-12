/**
 * UI 폴백용 (서버 응답 전·오프라인). 숫자는 백엔드 `ImportController` 의 `IMPORT_REVISION` 과 동일하게 유지할 것.
 */
export const IMPORT_REVISION = 13;

export function importBuildLabel(): string {
  return String(IMPORT_REVISION);
}
