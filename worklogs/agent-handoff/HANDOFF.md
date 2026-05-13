# PPT → Figma — 연속 작업·포맷 복구 핸드오프

**용도:** 이 PC를 포맷한 뒤, 다른 사람·다음 세션에서 **맥락 없이** 이어가기 위한 단일 진입 문서. 에이전트는 **이 파일을 먼저 읽는다.**  
**갱신 규칙 (2026-05-13):** **HANDOFF는 하루에 한 번만** 갱신한다. 당일 세부 작업·회고는 **`worklogs/YYYY-MM-DD/*.md`** 에만 적는다.

**경로:** `worklogs/agent-handoff/HANDOFF.md` (레포 루트 기준)  
**PRD:** 레포 루트 `2026-05-04-ppt-to-figma-plugin-prd.md`  
**원격 저장소:** `https://github.com/zerolist-coder/figma-to-pptx.git` (로컬 작업 폴더 예: `D:\figma`)

---

## 1. 포맷 후 복구 체크리스트

1. **Git** 설치 후 클론: `git clone https://github.com/zerolist-coder/figma-to-pptx.git` → 원하는 경로(예: `D:\figma`)에 둔다.
2. **JDK 21** — `backend/pom.xml`의 `<java.version>21</java.version>` 과 일치해야 한다.
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
| Vite(메인 번들) | `plugin/vite.main.config.ts` |
| 일자별 메모 | `worklogs/YYYY-MM-DD/*.md` |

---

## 4. API 응답 형태 (참고)

분석 성공 시 본문에 `data`(PptxData), `message`, `importId` 등이 포함된다. **`buildLabel` / `IMPORT_REVISION` 은 제거됨** (버전 숫자를 UI·알림에 표시하지 않음).

**URL:** 플러그인은 `http://localhost:3000/api/imports/analyze` 로 고정 호출한다. 포트를 바꾸면 `application.properties`와 `App.tsx`·`manifest.json`을 함께 맞출 것.

---

## 5. 지금까지 구현된 것 (요약 → 상세)

### 5.1 최근 커밋 맥락 (git)

- 초기 커밋 이후: 도형·텍스트 렌더 정확도 개선, **LayoutDTO/MasterDTO** 및 import 파이프라인·DTO·플러그인 동기화, **`ImportBuildInfo` 제거** 후 한때 `ImportController`+`import-build.ts` 로 빌드 라벨을 맞췄으나 **현재는 빌드 라벨 필드 자체를 쓰지 않음**.
- **2026-05-13:** **표(`TABLE`)** DTO·백엔드 추출·플러그인 렌더(FigJam `createTable` / Design·병합 시 RECT 폴백), **텍스트 OOXML** 보강(`a:fld`, `a:pPr` 정렬, `bodyPr`, `schemeClr`, 어두운 배경 위 글자색 보정), `poi-ooxml-full` 등 의존성 정리. 당일 세부는 **`worklogs/2026-05-13/2026-05-13.md`**. HANDOFF 갱신은 **하루 1회**로 고정.

### 5.2 백엔드

- **Apache POI `XMLSlideShow`** 로 슬라이드·마스터·레이아웃 순회, pt→px 등 좌표 스케일.
- **DTO:** `PptxDataDTO`, `SlideDTO`, `MasterDTO`, `LayoutDTO`, `ShapeDTO`, 텍스트용 `TextDTO` / 단락·런 DTO, **`TableDataDTO` / `TableCellPieceDTO`** (`ShapeDTO.type === "TABLE"` 일 때).
- **배경:** 단색(`backgroundFillHex`) 및 **배경 blip** (`XSLFBackground` → `backgroundImageBase64`) — OOXML **`srcRect` 있으면 서버 크롭** 후 전달, **`stretch`/`fillRect` 의 9-patch 는 슬라이드·배경 해상도로 PNG 합성**, **`tile` 이면** `backgroundImageFillStyle`(Figma `TILE`+`scalingFactor` 힌트) 포함, 상속(레이아웃→마스터 등) 시 픽셀·스타일 같이 전달.
- **도형:** 기본 프리셋(RECT, ELLIPSE, 화살 계열, QUAD_ARROW 근사 등), **그림 채우기(TexturePaint)·삽입 그림**(`spPr/blipFill` **srcRect 크롭**, **stretch nine-slice 가능 시 서버 처리**, **`tile` → `imageFillStyle`**) , **중첩 `XSLFGroupShape`** → `ShapeDTO.children`.
- **표:** **`XSLFTable`** → JSON **`type: "TABLE"`** + `table` (`numRows`/`numColumns`, `columnWidthsPx`/`rowHeightsPx`, `pieces`, `mergedCells`). 셀 위치는 **열·행 폭 누적**으로 계산. **`XSLFGraphicFrame`**(표 외): 차트/다이어그램은 생략, **폴백 그림** 있으면 `PICTURE`로.
- **텍스트:** OOXML **`a:fld`**(CTTextField) DOM 순서 추출, **`a:pPr/@algn`**, **`bodyPr` inset·anchor**, 테마 **`schemeClr`** 등 — **`poi-ooxml-full`** + DrawingML 타입 보강. 매우 어두운 채우기 위 **어두운 글자색**은 흰색 보정.
- **마스터/레이아웃:** 마스터·레이아웃 시트에서는 `isPlaceholder()` 도형 제외(슬라이드는 플레이스홀더 유지해 본문 텍스트 보존).
- **삽입 그림(PICTURE):** OOXML `srcRect`(blip 크롭)가 있으면 **서버에서 래스터 크롭** 후 PNG로 전달(LLM 필요 없음). ImageIO 불가(EMF 등) 시 원본 그대로.
- **기타:** 큰 이미지는 Base64 생략·플레이스홀더 처리 등 제한 있음(아래 한계 참고).

### 5.3 플러그인

- 분석 응답 래핑 차이 흡수 후 `PptxData`로 언랩 → 렌더.
- 페이지: **`00 PPT Masters`** — 플러그인 실행 중인 페이지에 마스터·레이아웃 배치 후 이름 변경, **`01 PPT Slides`** 는 슬라이드 전용 새 페이지(제한 시 한 페이지 병합). 마스터 없을 때 슬라이드만 로드하면 실행 페이지 이름을 **`00 PPT Slides · …`** 로 맞춤.
- 마스터/레이아웃 **컴포넌트** vs **백드롭**으로 배경 표현 분리(슬라이드 배경 가림 완화).
- **`clipsContent = false`** 로 프레임 밖 그룹 클리핑 완화.
- **`TABLE`:** **`figma.createTable`** 는 **FigJam 전용** API. **병합 없음 + API 존재** 시 네이티브 표로 렌더, 그 외(Figma Design·병합 표·실패)는 **프레임 + 셀 RECT** 폴백. 텍스트 셀은 기존 런 스타일 로직 재사용(`applyStyledRanges` 등).
- 표 포함 시 **`preloadFonts`**·**`shapeListHasGraphicContent`** 경로에 표 반영. 텍스트는 **멀티 런/단락**, 라벨·플레이스홀더 **이중 레이어**, `textAlignVertical`, 도형+텍스트 **그룹 스냅** 등 기존 개선과 병행.
- blip 채우기: OOXML **tile** → `imageFillStyle` / `backgroundImageFillStyle` 반영해 Figma **`TILE`+`scalingFactor`**; **stretch+fillRect** 는 서버에서 **9-patch PNG** 로 맞춘 뒤 **`FILL`**.

### 5.4 2026-05-11 작업 일지에 있던 추가 세부 (worklogs)

- **POI/XmlBeans:** `CTShape` 등에서 `isSetSpPr()` 미지원 시 `getSpPr() == null` 패턴으로 컴파일·런타임 정리.
- **화살:** `ShapeType`만으로 부족하면 DrawingML `prst` 읽기(`readPresetGeometryName` 등)로 보강.
- **API 응답:** `{ data, message, … }` vs 슬라이드 배열이 최상위인 경우 등 형태 차이 흡수.

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
| **표** | **부분** — `TABLE` DTO + 렌더(FigJam `createTable` 또는 Design 폴백); 병합·스타일 한계 |
| 배경 색/이미지 | 부분 (그라데이션 없음) |
| 테마 색 | 부분/미흡 |
| 마스터·레이아웃·컴포넌트 | 부분 |
| 슬라이드 Frame + Layout Instance | 부분 (인덱스 없으면 생략 등) |
| 누락 폰트 UX | 거의 없음 |
| 변환 경고 리포트 | 없음 |
| LLM | 없음 |

---

## 7. 알려진 한계·기술 부채

- 미처리/취약: **차트**, **커넥터**, 표 **Design 네이티브**(API 없음)·**셀 병합 시 FigJam 네이티브 불가**·격자 **이웃 셀 스트로크 이중** 등.
- 배경: **그라데이션**, blip **타일·stretch**, **삽입/배경/도형 blip 크롭** 일부 처리됨(EMF/ImageIO 불가 등은 원본)·**EMF/WMF**, theme `bgRef` 등.
- 이미지: 단일 파일 **~15MB 초과** 시 Base64 생략; Figma **4K 제한** 초과 시 실패 가능.
- 삼각형 `adj`, `adjustValues` XML 파싱 견고함은 샘플에 따라 추가 필요.
- **Import Report 페이지 (`00`)**, **추출 에셋 페이지 (`03`)** 미구현.
- 폰트 게이트·Font Review·재시도 플로 없음.

---

## 8. 앞으로 할 일 (우선순위 가이드)

사용자 요청이 최우선. 그다음 PRD 우선순위(3.3절)·로드맵(19절 근처)에 맞춘 권장 순서:

1. **이미지 완성도** — 크롭, EMF/대체, theme bgRef, 배경 blip 세부.
2. **도형 범위 확장** — 커넥터, 차트; 표는 Design에서 네이티브 API 확장 시 또는 오토레이아웃 격자 개선.
3. **배경** — 그라데이션, 고급 fill.
4. **`00 Import Report` / `03 Extracted Assets`** 페이지·리포트 JSON.
5. **폰트** — 누락 감지, 게이트, fallback UX, 재검사.
6. **LLM (있다면)** — 선택적 레이아웃 보정.
7. **품질** — 도형 매핑 표준화, 경고 수집, 대용량 UX, 내부 샘플 회귀.

세부 백로그(예: `QUAD_ARROW`를 `custGeom`/path로 정확히, 텍스트 스타일 회귀용 `.pptx` 세트)는 위 항목에 붙여 기록하면 된다.

---

## 9. 문서·리비전 유지 규칙

1. **포트·API URL 변경 시** — `application.properties`, `App.tsx`, `manifest.json` 함께 확인.
2. **Phase/범위 판단이 바뀌면** — 섹션 6 표 업데이트.
3. **큰 기능 완료 시** — 섹션 5(지금까지)와 7(한계) 정합성 점검.
4. **날짜 로그** — `worklogs/YYYY-MM-DD/*.md`에 **당일 상세**; HANDOFF는 **하루 1회** 스냅샷·의사결정 위주.

---

## 10. 관련 파일 참조

- PRD: `2026-05-04-ppt-to-figma-plugin-prd.md`
- 작업 일지 예: `worklogs/2026-05-11/2026-05-11.md`
