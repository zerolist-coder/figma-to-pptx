# PPT → Figma — 에이전트 연속 작업 가이드

> **용도:** 다른 에이전트가 이 파일을 **먼저 읽고** 히스토리·용어·현재 위치·다음 작업을 맞춘다.  
> **위치:** `D:\figma\worklogs\agent-handoff\HANDOFF.md`  
> **기준 문서:** [2026-05-04-ppt-to-figma-plugin-prd.md](../../2026-05-04-ppt-to-figma-plugin-prd.md)

**PRD와 구현이 “다르게” 느껴지는 이유 (한 줄):** PRD는 직접 OpenXML·폰트 게이트·이미지·리포트·선택형 LLM까지 넓게 잡혀 있고, 레포는 **Apache POI + JSON DTO + 플러그인**으로 Phase 1·2를 동시에 밟는 **MVP 경로**로 수렴했다.

---

## §0 용어 정리 — Phase / Step / Revision

| 용어 | 의미 | 지금 레포 |
|------|------|-----------|
| **PRD Phase 1~5** | PRD §19 구현 단계 — 제품·기능 단위 로드맵 | Phase **1·2 부분**, **3·4 거의 없음**, **5 일부** (아래 §2·§2.5) |
| **`PIPELINE_STEP` (`step1`)** | `ImportBuildInfo` / `import-build.ts`의 **문자열 파이프라인 이름** | **`step1`만 사용** — PRD “Phase 1”과 **같지 않음** |
| **`REVISION`** | 같은 `step1` 안에서 **빌드 추적용 정수** (`step1 - N`) | §5.1과 코드 **반드시 동기** |
| **`step2`, `step3`** | 예: 분석 전용 / 풀 임포트 등 **다른 배치**가 생길 때 붙일 **후보 이름** | **미정의·미도입** — 파이프라인을 나눌 때 서버·플러그인·본 문서 §4를 함께 수정 |

---

## §1 세션 시작 체크리스트 (에이전트용)

1. **§0**으로 Phase vs `step1` 혼동을 제거한다.  
2. **§2** PRD Phase 표·**§2.5** §3.1 충족도로 “어디까지 됐는지”를 본다.  
3. **§3** 히스토리로 맥락을 잡는다.  
4. **§5**·**§7**으로 상태와 다음 작업을 확인한다.  
5. **마스터/레이아웃·API 상세**는 **§6**을 본다.  
6. 동작 변경 후 추적이 필요하면 **§4** 리비전 규칙에 따라 `REVISION`과 §5.1을 갱신한다.  
7. 날짜별 일지는 `D:\figma\worklogs\YYYY-MM-DD\` 에만 두고, 여기서는 **스냅샷**만 유지한다.

---

## §2 PRD 구현 단계 (Phase 1 ~ 5) — §19 정렬

PRD 원문과 실제 구현 경로가 다를 수 있음을 **진행도 메모**에 명시한다.

| Phase | 이름 | PRD 목표(요약) | 이 레포 진행도 + 구현 경로 메모 |
|-------|------|----------------|--------------------------------|
| **1** | 파서와 중간 모델 | `.pptx` → 안정적인 중간 JSON | **진행 중.** PRD의 직접 unzip·relationship·theme·media 전부는 **아님** → **`XMLSlideShow` + DTO**로 축소. 마스터·레이아웃 트리는 JSON에 포함. |
| **2** | Figma 노드 생성 | JSON → 씬 그래프 | **진행 중.** `01`/`02` 페이지, 마스터·레이아웃 Instance, 텍스트·도형·단색·**배경 그림 blip**·**삽입 그림(`PICTURE`)**·**도형 blip(`TexturePaint`)**·**중첩 `GROUP`**. **크롭·타일·그라데이션 배경**, Import Report(`00`), 에셋(`03`), PRD 네이밍 등 미달. |
| **3** | 폰트 검증 UX | 누락 폰트·fallback·재시도 | **거의 미착수.** 폰트 로드 실패 시 Inter 등 **자동 대체** 수준; Font Review·게이트·재검사 없음. |
| **4** | AI 레이아웃 보정 | 선택적 LLM | **미착수.** |
| **5** | 품질 강화 | 실패율·리포트 | **일부.** 도형/텍스트 매핑은 계속 추가; PRD “내부 샘플 20개·추적 가능 리포트”는 목표로 남음. |

### §2.1 현재 위치 (한 문장)

**PRD 관점:** Phase **1·2를 교차로** 진행 중이며, Phase 1은 “PRD 문장 그대로의 파서”가 아니라 **POI 기반 파서**로 대체된 상태다. **코드 `step` 관점:** **`step1` 단일 파이프라인** + **`REVISION`만 증가**한다.

---

## §2.5 PRD §3.1 “1차 범위 포함” 대비 구현 상태

| PRD §3.1 항목 | 상태 | 비고 |
|----------------|------|------|
| `.pptx` 업로드 | 완료 | 플러그인 UI → `POST /api/imports/analyze` |
| PPTX unzip·OpenXML **직접** 파싱 | **미구현** | Apache POI가 패키지 처리 |
| 슬라이드 크기·목록 | 완료 | |
| 슬라이드별 텍스트 | 부분 | 단락/run·일부 정렬; 복잡 스타일 제한 |
| 슬라이드별 **이미지** | **부분** | **삽입 그림**·**도형 fill blip**·**그룹 내부** 동일. **슬라이드·마스터·레이아웃 배경 blip**(`XSLFBackground` `TexturePaint`, rev 13)·**크롭·타일·EMF** 등은 제한 |
| 기본 도형 | 부분 | RECT·ELLIPSE·TRIANGLE·RIGHT_ARROW 등; 기타는 RECT 폴백 |
| 배경 (색/이미지) | **부분** | **단색** + **그림 blip 배경**(상속). **그라데이션** 없음 |
| 테마 색상 | 부분/미흡 | 필요 시 PRD 범위 재정의 |
| 마스터·레이아웃 추출·컴포넌트 | 부분 | 구조 반영; **플레이스홀더는 슬라이드용 레이아웃 인스턴스에서 제외**(마스터 페이지 미리보기에는 동일 데이터로 박스가 없을 수 있음) |
| 슬라이드 Frame + Layout Instance | 부분 | 인덱스 없으면 Instance 생략 |
| 누락 폰트 감지·안내·fallback 선택 | **거의 없음** | 게이트·리뷰 UI 없음 |
| 변환 경고 리포트 | **없음** | |
| 선택형 LLM 보정 | **없음** | |

---

## §3 프로젝트 히스토리 (요약)

1. **2026-05-04 PRD** — 문제·범위(§3)·시나리오·페이지(`00`~`03`)·데이터 흐름·**Phase 1~5**(§19)·MVP 지표(§20) 정의.  
2. **구현 전략** — PRD의 “직접 OpenXML 파서” 대신 **POI `XMLSlideShow` + Spring Boot + JSON DTO**로 중간 모델 단순화.  
3. **플러그인** — 업로드·분석 응답 → [plugin/src/plugin/main.ts](../../plugin/src/plugin/main.ts)에서 노드 생성; 메인 번들 `es2017` 등 호환 처리.  
4. **좌표·텍스트·도형** — pt→px, 단락/run, 회전·점선·삼각형·rightArrow(DrawingML 기준)·도형+텍스트 그룹.  
5. **마스터/레이아웃** — `MasterDTO`/`LayoutDTO`, `masterIndex`/`layoutIndex`, `renderPptStructure`, Figma `01 PPT Masters` / `02 PPT Slides`.  
6. **단색 배경** — `backgroundFillHex`, 슬라이드는 레이아웃→마스터 상속 해석 후 Frame/Component `fills`.  
7. **운영** — `buildLabel`, SLF4J, `REVISION` 동기, 본 HANDOFF 유지.  
8. **도형 그림 채우기** — `XSLFSimpleShape` `TexturePaint` → `imageBase64`; 플러그인에서 RECT/VECTOR 등 `IMAGE` fill.  
9. **마스터·레이아웃 vs 슬라이드** — 마스터/레이아웃 시트에서만 `isPlaceholder()` 도형 제외(rev 8). 슬라이드는 플레이스홀더 포함(본문 텍스트 유지). `01` 마스터 페이지에는 플레이스홀더 박스가 더 이상 나오지 않을 수 있음.  
10. **Figma 페이지 상한** — `createPage` 실패 시(예: Starter 3페이지) 한 페이지에 마스터 대역·슬라이드 대역을 세로로 이어 배치(rev 9).  
11. **마스터 페이지 자동** — 마스터·레이아웃 JSON에 **내보낼 도형(빈 GROUP 제외)**이 없으면 `renderSlides`만(rev 10–11).  
12. **최상위·중첩 `XSLFGroupShape`** — `ShapeDTO.children`, chOff/chExt 사상, Figma `Frame`(rev 12).  
13. **배경 그림** — `XSLFBackground` blip → `backgroundImageBase64`(슬라이드·마스터·레이아웃, 상속; rev 13).

---

## §4 파이프라인 태그 (`step1`) · 리비전 규칙

| 항목 | 파일 | 필드 | 현재 값 |
|------|------|------|---------|
| 서버 | `backend/.../ImportBuildInfo.java` | `PIPELINE_STEP` | `step1` |
| 플러그인 | `plugin/src/constants/import-build.ts` | `IMPORT_PIPELINE_STEP` | `step1` |

- **표시:** `step1 - {REVISION}` (예: `step1 - 13`).  
- **`step2` 도입 시:** 서버·플러그인·이 문서를 동시에 수정하고, “무엇이 어떤 step인지” 한 줄을 §0에 추가한다.  
- **리비전:** 아래 두 필드를 **항상 같은 정수**로 유지한다.

| 항목 | 파일 | 필드 |
|------|------|------|
| 서버 | `backend/.../ImportBuildInfo.java` | `REVISION` |
| 플러그인 | `plugin/src/constants/import-build.ts` | `IMPORT_REVISION` |

- **올릴 때:** 플러그인/백엔드 **사용자 동작**이 바뀌어 빌드를 구분해야 할 때.  
- **안 올려도 될 때:** 문서만, 주석만, 오탈자만.  
- **PRD Phase 진척 ≠ 리비전 자동 증가.**

> **코드가 진실:** HANDOFF §5.1 숫자는 저장소의 `REVISION`과 맞출 것.

---

## §5 현재 진행 상황

### §5.1 빌드 태그 (코드와 동기)

- `PIPELINE_STEP` / `IMPORT_PIPELINE_STEP`: `step1`  
- `ImportBuildInfo.REVISION` / `IMPORT_REVISION`: **13**  
- 라벨 예: **`step1 - 13`**

### §5.2 이미 반영된 큰 덩어리

- 백엔드: [PptxService.java](../../backend/src/main/java/com/ppttofigma/backend/service/PptxService.java) — pt→px, 마스터·레이아웃·슬라이드 shape, 레이아웃 매핑, 단색·**배경 blip**(`XSLFBackground`→`backgroundImageBase64`), 도형 **`TexturePaint`/`PICTURE`**, 플레이스홀더 제외(마스터·레이아웃), **`GROUP` + `children`**.  
- 플러그인: 멀티 run/단락, **`GROUP` Frame**, **`applyFrameBackground`**(배경 이미지 우선·단색 폴백), 마스터/레이아웃 페이지·Instance, **`PICTURE`·도형** IMAGE fill, **`createPage` 실패 시** 세로 병합.  
- 로그/태그: `buildLabel`, SLF4J.

### §5.3 알려진 한계 (기술 부채)

- 도형 추출: **`XSLFGroupShape`(중첩)·`XSLFPictureShape`·`XSLFSimpleShape`** — `CTGraphicalObjectFrame`(차트 등)·`CTConnector`·`XSLFTable` 등은 미처리.  
- 삼각형 `adj`, `adjustValues` XML 스캔 견고함.  
- 배경 **그라데이션**, blip **크롭·타일·stretch** 해석, **EMF/WMF**, **`00`/`03`**, Font 게이트·리포트, LLM 없음.  
- 그림 단일 파일 **약 15MB 초과** 시 Base64 생략(플레이스홀더 사각형). Figma 이미지 **4K 한도** 초과 시 실패 가능(회색).  
- 레이아웃 미해석 시 슬라이드만 그림.

---

## §6 마스터·레이아웃 (코드 기준 상세)

#### 백엔드

| 항목 | 동작 |
|------|------|
| 마스터 | `getSlideMasters()` → `MasterDTO` (`name`, `backgroundFillHex`, **`backgroundImageBase64`**, `shapes`, `layouts`) |
| 레이아웃 | `getSlideLayouts()` → `LayoutDTO` (단색·**배경 그림**은 레이아웃 없으면 마스터 폴백) |
| 슬라이드 | `SlideDTO`: `backgroundFillHex`·**`backgroundImageBase64`**(상속), `masterIndex`, `layoutIndex`, `shapes` |
| 매핑 | `IdentityHashMap<XSLFSlideLayout, int[]>` |
| 배경 | 단색: `getBackground().getFillColor()`; 그림: `getFillStyle().getPaint()` `TexturePaint` |

#### 플러그인

- `hasRenderableMasterLayoutShapes(data)` 이면 `renderPptStructure`, 아니면 `renderSlides`.  
- `01` / `02` 페이지, Layout = Master Instance + 레이아웃 shape.

#### 아직 아닌 것

리포트 페이지, 플레이스홀더 완전 재현, **배경 그라데이션·theme bgRef·blip 크롭/타일**, **커넥터·차트·표** 등.

---

## §7 다음 작업 — PRD §3.3 우선순위와 매핑

사용자 요청이 있으면 그쪽이 우선. 아래는 **PRD 지원 우선순위(§3.3) + §19**에 맞춘 **권장 순서**다.

| 순서 | 작업 | PRD 참조 | 비고 |
|------|------|-----------|------|
| 1 | **이미지** (슬라이드·채우기) | §3.3 4, Phase 2 “Image Fill” | **삽입·도형 fill·배경 blip** → **크롭·EMF·theme bgRef** |
| 2 | **그룹·비-simple shape** | §3.3 5, shape 확장 | **GROUP 1차(rev 12)**; 커넥터·차트·표 등 |
| 3 | **배경 이미지·그라데이션** | §3.1 배경, §3.3 2 | |
| 4 | **`00 Import Report` · `03 Extracted Assets`** | §8 페이지, Phase 2 리포트 | |
| 5 | **Font Review·게이트·재검사** | §3.3 6, Phase 3, 시나리오 B | |
| 6 | **LLM 보정(선택)** | §3.3 7, Phase 4 | |
| 7 | **품질 강화** — 도형 매핑·크롭·경고·대용량 UX | §3.3 8, Phase 5 | |

세부 백로그(삼각형 `adj`, `adjustValues` 파싱 등)는 위 항목과 겹치지 않으면 §7에 하위 bullet로 추가해 갱신한다.

---

## §8 빠른 파일 맵

| 영역 | 경로 |
|------|------|
| PRD | `2026-05-04-ppt-to-figma-plugin-prd.md` |
| pptx 분석 | `backend/.../service/PptxService.java` |
| DTO | `backend/.../dto/*.java` |
| Figma 렌더 | `plugin/src/plugin/main.ts` |
| 빌드 태그 | `ImportBuildInfo.java`, `plugin/src/constants/import-build.ts` |
| Vite 메인 | `plugin/vite.main.config.ts` |
| 날짜 로그 | `worklogs/YYYY-MM-DD/*.md` |

---

## §9 이 문서 갱신 규칙

1. `REVISION` 변경 시 **§5.1** 즉시 수정.  
2. PRD Phase 해석이 바뀌면 **§2** 표 수정.  
3. 범위 착수/완료 시 **§2.5** 행 상태 갱신.  
4. 마스터/레이아웃·배경 로직 변경 시 **§6**·**§5.3** 수정.  
5. `PIPELINE_STEP` 추가/변경 시 **§0·§4·§5.1** 동시 수정.  
6. §7에서 끝낸 일은 **§2.5·§5.2**에 반영.

---

*문서 저장 후 `REVISION`과 §5.1이 일치하는지 다시 확인할 것.*
