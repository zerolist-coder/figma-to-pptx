# PPT → Figma — 연속 작업·포맷 복구 핸드오프

**용도:** 이 PC를 포맷한 뒤, 다른 사람·다음 세션에서 **맥락 없이** 이어가기 위한 단일 진입 문서. 에이전트는 **이 파일을 먼저 읽는다.**  
**경로:** `worklogs/agent-handoff/HANDOFF.md` (레포 루트 기준)  
**PRD:** 레포 루트 `2026-05-04-ppt-to-figma-plugin-prd.md`  
**원격 저장소:** `https://github.com/zerolist-coder/figma-to-pptx.git` (로컬 작업 폴더 예: `D:\figma`)

---

## 1. 포맷 후 복구 체크리스트

1. **Git** 설치 후 클론: `git clone https://github.com/zerolist-coder/figma-to-pptx.git` → 원하는 경로(예: `D:\figma`)에 둔다.
2. **JDK 17** — `backend/pom.xml`의 `<java.version>17</java.version>` 과 일치해야 한다.
3. **Node.js** — 플러그인 빌드용(예: LTS). `plugin/package.json`은 Vite 5, TypeScript 5, React 18.
4. **백엔드 기동:**  
   `cd backend` → `mvn spring-boot:run` (또는 IDE에서 `PptToFigmaApplication` 실행).  
   - **포트:** `application.properties`에 `server.port=3000` (플러그인 `fetch` URL과 동일해야 함).  
   - **업로드 한도:** 멀티파트 최대 50MB.
5. **플러그인:** `cd plugin` → `npm install` → `npm run build` (또는 `npm run dev`로 UI 개발).  
   Figma Desktop → Plugins → Development → **Import plugin from manifest** → `plugin/manifest.json`.  
   `manifest.json`의 `networkAccess`에 `http://localhost:3000`이 있어야 분석 API 호출 가능.
6. **동작 확인:** 플러그인에서 `.pptx` 업로드 → `POST /api/imports/analyze` → JSON이 메인 스레드로 전달되어 캔버스에 노드 생성.

---

## 2. 제품 한 줄·아키텍처

**목표:** PowerPoint(`.pptx`)를 업로드하면 서버가 내용을 읽어 JSON으로 돌려주고, Figma 플러그인이 그 JSON으로 프레임·텍스트·도형·이미지 등을 재구성한다.

**데이터 흐름:**

```
.pptx 업로드 (플러그인 UI)
  → POST http://localhost:3000/api/imports/analyze
  → Spring Boot + Apache POI (XMLSlideShow)
  → PptxDataDTO (JSON)
  → UI postMessage → plugin main (sandbox) → figma.* API
```

**PRD와 구현의 괴리 (한 줄):** PRD는 직접 OpenXML·폰트 게이트·리포트·선택 LLM 등 넓은 범위를 말하지만, 레포는 **POI + DTO + 플러그인**으로 **MVP 경로**를 밟는다.

---

## 3. 레포 구조 (빠른 맵)

| 영역 | 경로 |
|------|------|
| PPTX 파싱·DTO | `backend/src/main/java/com/ppttofigma/backend/service/PptxService.java`, `.../dto/*.java` |
| REST | `backend/.../controller/ImportController.java` |
| Figma 렌더 | `plugin/src/plugin/main.ts` |
| 업로드 UI (`fetch`) | `plugin/src/ui/App.tsx` |
| 빌드 리비전 (UI 폴백) | `plugin/src/constants/import-build.ts` |
| Vite(메인 번들) | `plugin/vite.main.config.ts` |
| 일자별 메모 | `worklogs/YYYY-MM-DD/*.md` |

---

## 4. 코드 기준 “진실” — 빌드 라벨·리비전

과거 문서에는 `step1 - N` 형태와 `ImportBuildInfo.java` / `IMPORT_PIPELINE_STEP` 이 등장했으나, **현재 저장소 기준**은 아래와 같다.

| 항목 | 위치 | 설명 |
|------|------|------|
| 서버 리비전 | `ImportController.IMPORT_REVISION` (`int`) | 분석 응답의 `buildLabel`에 **정수를 문자열로** 넣음 (예: `"13"`). |
| 플러그인 폴백 | `import-build.ts`의 `IMPORT_REVISION` | 서버 응답 전·오프라인 시 `importBuildLabel()` = `String(IMPORT_REVISION)`. |
| 동기 규칙 | 위 두 값을 **항상 같은 정수**로 유지 | 사용자에게 보이는 동작·파이프라인 결과가 바뀌어 빌드를 구분해야 할 때만 증가. 문서만 고친 경우는 불필요. |

**URL:** 플러그인은 `http://localhost:3000/api/imports/analyze` 로 고정 호출한다. 포트를 바꾸면 `application.properties`와 `App.tsx`·`manifest.json`을 함께 맞출 것.

---

## 5. 지금까지 구현된 것 (요약 → 상세)

### 5.1 최근 커밋 맥락 (git)

- 초기 커밋 이후: 도형·텍스트 렌더 정확도 개선, **LayoutDTO/MasterDTO** 및 import 파이프라인·DTO·플러그인 동기화, **`ImportBuildInfo` 제거** 후 리비전은 **`ImportController` + `import-build.ts`** 로만 관리.

### 5.2 백엔드

- **Apache POI `XMLSlideShow`** 로 슬라이드·마스터·레이아웃 순회, pt→px 등 좌표 스케일.
- **DTO:** `PptxDataDTO`, `SlideDTO`, `MasterDTO`, `LayoutDTO`, `ShapeDTO`, 텍스트용 `TextDTO` / 단락·런 DTO 등.
- **배경:** 단색(`backgroundFillHex`) 및 **배경 blip** (`XSLFBackground` → `backgroundImageBase64`), 상속(레이아웃→마스터 등).
- **도형:** 기본 프리셋(RECT, ELLIPSE, 화살 계열, QUAD_ARROW 근사 등), **그림 채우기·삽입 그림**, **중첩 `XSLFGroupShape`** → `ShapeDTO.children`.
- **마스터/레이아웃:** 마스터·레이아웃 시트에서는 `isPlaceholder()` 도형 제외(슬라이드는 플레이스홀더 유지해 본문 텍스트 보존).
- **기타:** 큰 이미지는 Base64 생략·플레이스홀더 처리 등 제한 있음(아래 한계 참고).

### 5.3 플러그인

- 분석 응답 래핑 차이 흡수 후 `PptxData`로 언랩 → 렌더.
- 페이지: **`01 PPT Masters`**, **`02 PPT Slides`** (필요 시 한 페이지에 세로 병합 — Starter 플랜 등 페이지 수 제한 대응).
- 마스터/레이아웃 **컴포넌트** vs **백드롭**으로 배경 표현 분리(슬라이드 배경 가림 완화).
- **`clipsContent = false`** 로 프레임 밖 그룹 클리핑 완화.
- 텍스트: 멀티 런/단락, 가로 화살·QUAD_ARROW 등과 연동된 도형 생성 헬퍼.
- `buildLabel` 표시: 서버 값 우선, 없으면 `importBuildLabel()`.

### 5.4 2026-05-11 작업 일지에 있던 추가 세부 (worklogs)

- **POI/XmlBeans:** `CTShape` 등에서 `isSetSpPr()` 미지원 시 `getSpPr() == null` 패턴으로 컴파일·런타임 정리.
- **화살:** `ShapeType`만으로 부족하면 DrawingML `prst` 읽기(`readPresetGeometryName` 등)로 보강.
- **API 응답:** `{ data, buildLabel }` vs 슬라이드 배열 최상위 등 형태 차이 흡수.

---

## 6. PRD Phase 대비 진행도 (요약 표)

PRD 문서의 Phase 1~5(로드맵)와 레포 구현은 1:1이 아니다. `PIPELINE_STEP` 같은 예전 용어와 PRD “Phase 1”을 혼동하지 말 것.

| Phase | PRD 쪽 의미 | 이 레포 |
|-------|----------------|---------|
| 1 | 안정적인 중간 표현 | **진행 중** — POI+D로 축소; 직접 unzip/relationship 전부는 아님. 마스터·레이아웃 트리 JSON 포함. |
| 2 | Figma 노드 생성 | **진행 중** — 텍스트·도형·단색·배경 blip·삽입 그림·TexturePaint·GROUP 등. Import Report `00`, 에셋 `03`, 일부 고급 배경/크롭은 미달. |
| 3 | 폰트 검증 UX | **거의 없음** — 자동 폰트 대체 수준. |
| 4 | LLM 보정 | **없음** |
| 5 | 품질·리포트 | **일부** — 매핑 확장 중; PRD 수준 리포트·샘플 세트는 목표로 남음. |

### 6.1 PRD “1차 범위”(문서 3.1절) 체크리스트

| 항목 | 상태 |
|------|------|
| `.pptx` 업로드 | 완료 |
| OpenXML 직접 파싱 | **아님** (POI가 패키지 처리) |
| 슬라이드 크기·목록 | 완료 |
| 슬라이드 텍스트 | 부분 (run/단락·정렬 일부) |
| 이미지 | 부분 (삽입·도형 fill·배경 blip·그룹 내부; 크롭·타일·EMF 등 제한) |
| 기본 도형 | 부분 (일부는 RECT 폴백) |
| 배경 색/이미지 | 부분 (그라데이션 없음) |
| 테마 색 | 부분/미흡 |
| 마스터·레이아웃·컴포넌트 | 부분 |
| 슬라이드 Frame + Layout Instance | 부분 (인덱스 없으면 생략 등) |
| 누락 폰트 UX | 거의 없음 |
| 변환 경고 리포트 | 없음 |
| LLM | 없음 |

---

## 7. 알려진 한계·기술 부채

- 미처리/취약: **차트(`CTGraphicalObjectFrame`)**, **커넥터**, **표(`XSLFTable`)** 등.
- 배경: **그라데이션**, blip **크롭·타일·stretch**, **EMF/WMF**, theme `bgRef` 등.
- 이미지: 단일 파일 **~15MB 초과** 시 Base64 생략; Figma **4K 제한** 초과 시 실패 가능.
- 삼각형 `adj`, `adjustValues` XML 파싱 견고함은 샘플에 따라 추가 필요.
- **Import Report 페이지 (`00`)**, **추출 에셋 페이지 (`03`)** 미구현.
- 폰트 게이트·Font Review·재시도 플로 없음.

---

## 8. 앞으로 할 일 (우선순위 가이드)

사용자 요청이 최우선. 그다음 PRD 우선순위(3.3절)·로드맵(19절 근처)에 맞춘 권장 순서:

1. **이미지 완성도** — 크롭, EMF/대체, theme bgRef, 배경 blip 세부.
2. **도형 범위 확장** — 커넥터, 차트, 표, 비-simple shape.
3. **배경** — 그라데이션, 고급 fill.
4. **`00 Import Report` / `03 Extracted Assets`** 페이지·리포트 JSON.
5. **폰트** — 누락 감지, 게이트, fallback UX, 재검사.
6. **LLM (있다면)** — 선택적 레이아웃 보정.
7. **품질** — 도형 매핑 표준화, 경고 수집, 대용량 UX, 내부 샘플 회귀.

세부 백로그(예: `QUAD_ARROW`를 `custGeom`/path로 정확히, 텍스트 스타일 회귀용 `.pptx` 세트)는 위 항목에 붙여 기록하면 된다.

---

## 9. 문서·리비전 유지 규칙

1. **`IMPORT_REVISION` 변경 시** — `ImportController`와 `import-build.ts` **동시** 수정, 본 문서 **섹션 4** 숫자 갱신.
2. **포트·API URL 변경 시** — `application.properties`, `App.tsx`, `manifest.json` 함께 확인.
3. **Phase/범위 판단이 바뀌면** — 섹션 6 표 업데이트.
4. **큰 기능 완료 시** — 섹션 5(지금까지)와 7(한계) 정합성 점검.
5. **날짜 로그** — `worklogs/YYYY-MM-DD/*.md`에만 상세 일지; HANDOFF는 스냅샷·의사결정 위주.

---

## 10. 관련 파일 참조

- PRD: `2026-05-04-ppt-to-figma-plugin-prd.md`
- 작업 일지 예: `worklogs/2026-05-11/2026-05-11.md`

*마지막으로 저장소의 `IMPORT_REVISION`과 이 문서 섹션 4가 일치하는지 확인할 것.*
