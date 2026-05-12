// ---------------------------------------------------------------------------
// Backend DTO와 1:1 매칭되는 타입 정의 (PptxDataDTO 등)
// ---------------------------------------------------------------------------

interface PptxData {
  fileName: string;
  width: number;
  height: number;
  slides: SlideData[];
  masters?: MasterData[];
}

interface MasterData {
  name: string;
  /** 마스터 시트 단색 배경 */
  backgroundFillHex?: string;
  backgroundImageBase64?: string;
  backgroundImageMimeType?: string;
  shapes: ShapeData[];
  layouts: LayoutData[];
}

interface LayoutData {
  name: string;
  backgroundFillHex?: string;
  backgroundImageBase64?: string;
  backgroundImageMimeType?: string;
  shapes: ShapeData[];
}

interface SlideData {
  slideNumber: number;
  masterIndex?: number;
  layoutIndex?: number;
  /** 슬라이드→레이아웃→마스터 상속 해석 단색 배경 */
  backgroundFillHex?: string;
  backgroundImageBase64?: string;
  backgroundImageMimeType?: string;
  shapes: ShapeData[];
}

interface ShapeData {
  type: string;
  x: number;
  y: number;
  width: number;
  height: number;
  fillHex?: string;
  strokeHex?: string;
  strokeWeight?: number;
  rotation: number;
  flipHorizontal?: boolean;
  flipVertical?: boolean;
  dashStyle?: string;
  /** POI LineDash.pattern — strokeWeight(px)와 곱함 */
  dashPatternMultipliers?: number[];
  adjustValues?: number[];
  text?: TextData;
  /** 그림 채우기·삽입 그림: Base64 원본 바이트 */
  imageBase64?: string;
  imageMimeType?: string;
  /** GROUP: 자식 도형 */
  children?: ShapeData[];
}

interface TextData {
  content?: string;
  fontSize?: number;
  colorHex?: string;
  align?: string;
  paragraphs?: TextParagraph[];
}

interface TextParagraph {
  align?: string;
  runs: TextRun[];
}

interface TextRun {
  text: string;
  fontFamily?: string;
  fontSize?: number;
  colorHex?: string;
  bold?: boolean;
  italic?: boolean;
  underline?: boolean;
}

import { importBuildLabel } from '../constants/import-build';

// ---------------------------------------------------------------------------
// Plugin entry
// ---------------------------------------------------------------------------

figma.showUI(__html__, { width: 400, height: 300 });

figma.ui.onmessage = async (msg) => {
  if (msg.type === 'analyze-complete') {
    const wrapped = msg.data as { data?: PptxData; buildLabel?: string } | PptxData;
    const data: PptxData | undefined =
      wrapped &&
      typeof wrapped === 'object' &&
      'slides' in wrapped &&
      !('data' in wrapped && (wrapped as { data?: PptxData }).data)
        ? (wrapped as PptxData)
        : (wrapped as { data?: PptxData }).data;
    if (!data) return;

    const tag =
      (wrapped && typeof wrapped === 'object' && 'buildLabel' in wrapped
        ? (wrapped as { buildLabel?: string }).buildLabel
        : undefined) ?? importBuildLabel();
    const fn = data.fileName ?? '(이름 없음)';

    figma.notify(`[${tag}] ${fn} 변환 시작…`);

    try {
      await preloadFonts(data);
      const useMasterLayoutsPage = hasRenderableMasterLayoutShapes(data);
      const nodes = useMasterLayoutsPage
        ? await renderPptStructure(data)
        : await renderSlides(data);
      figma.viewport.scrollAndZoomIntoView(nodes);
      figma.notify(`[${tag}] 변환 완료`);
    } catch (err: any) {
      figma.notify(`[${tag}] 변환 실패: ${err.message ?? err}`);
    }
  }
};

// ---------------------------------------------------------------------------
// Font handling
// ---------------------------------------------------------------------------

const DEFAULT_FONT_FAMILY = 'Inter';

function fontStyleOf(run: TextRun): string {
  const bold = !!run.bold;
  const italic = !!run.italic;
  if (bold && italic) return 'Bold Italic';
  if (bold) return 'Bold';
  if (italic) return 'Italic';
  return 'Regular';
}

async function preloadFonts(data: PptxData): Promise<void> {
  const seen = new Set<string>();
  const tasks: Promise<void>[] = [];

  function enqueue(family: string, style: string) {
    const key = `${family}::${style}`;
    if (seen.has(key)) return;
    seen.add(key);
    tasks.push(
      figma
        .loadFontAsync({ family, style })
        .catch(async () => {
          // 원본 폰트가 없으면 Inter 같은 스타일로 대체 시도
          await figma.loadFontAsync({ family: DEFAULT_FONT_FAMILY, style }).catch(async () => {
            await figma.loadFontAsync({ family: DEFAULT_FONT_FAMILY, style: 'Regular' });
          });
        })
    );
  }

  // 기본 fallback 후보를 항상 미리 로드
  enqueue(DEFAULT_FONT_FAMILY, 'Regular');
  enqueue(DEFAULT_FONT_FAMILY, 'Bold');
  enqueue(DEFAULT_FONT_FAMILY, 'Italic');
  enqueue(DEFAULT_FONT_FAMILY, 'Bold Italic');

  function enqueueShapeText(shape: ShapeData) {
    const text = shape.text;
    if (!text) return;

    const paragraphs = text.paragraphs ?? [];
    if (paragraphs.length === 0) {
      enqueue(DEFAULT_FONT_FAMILY, 'Regular');
      return;
    }
    for (const paragraph of paragraphs) {
      for (const run of paragraph.runs) {
        const family = run.fontFamily?.trim() || DEFAULT_FONT_FAMILY;
        enqueue(family, fontStyleOf(run));
      }
    }
  }

  function walkShape(shape: ShapeData) {
    enqueueShapeText(shape);
    for (const c of shape.children ?? []) {
      walkShape(c);
    }
  }

  for (const slide of data.slides) {
    for (const shape of slide.shapes) {
      walkShape(shape);
    }
  }

  if (data.masters) {
    for (const m of data.masters) {
      for (const shape of m.shapes) {
        walkShape(shape);
      }
      for (const lay of m.layouts) {
        for (const shape of lay.shapes) {
          walkShape(shape);
        }
      }
    }
  }

  await Promise.all(tasks);
}

/** PPT에 슬라이드 마스터가 하나라도 있으면 01 PPT Masters·레이아웃 페이지를 만든다. */
function hasRenderableMasterLayoutShapes(data: PptxData): boolean {
  const n = data.masters?.length ?? 0;
  return n > 0;
}

// 로드 시도 → 실패하면 Inter로 대체한 FontName을 반환
async function resolveFont(family: string | undefined, style: string): Promise<FontName> {
  const target = family?.trim() || DEFAULT_FONT_FAMILY;
  try {
    await figma.loadFontAsync({ family: target, style });
    return { family: target, style };
  } catch {
    // 동일 스타일 Inter → 그것도 실패하면 Inter Regular
    try {
      await figma.loadFontAsync({ family: DEFAULT_FONT_FAMILY, style });
      return { family: DEFAULT_FONT_FAMILY, style };
    } catch {
      await figma.loadFontAsync({ family: DEFAULT_FONT_FAMILY, style: 'Regular' });
      return { family: DEFAULT_FONT_FAMILY, style: 'Regular' };
    }
  }
}

// ---------------------------------------------------------------------------
// 마스터/레이아웃 컴포넌트 + 슬라이드 Frame (PRD §8)
// ---------------------------------------------------------------------------

/** Starter 플랜 등으로 페이지 상한 초과 시 createPage가 예외를 던질 수 있음 */
function tryCreateNamedPage(name: string): PageNode | null {
  try {
    const p = figma.createPage();
    p.name = name;
    return p;
  } catch {
    return null;
  }
}

function maxChildBottomY(page: PageNode): number {
  let max = 0;
  for (const n of page.children) {
    if (n.type === 'COMPONENT' || n.type === 'FRAME' || n.type === 'INSTANCE' || n.type === 'GROUP') {
      max = Math.max(max, n.y + n.height);
    }
  }
  return max;
}

async function renderPptStructure(data: PptxData): Promise<SceneNode[]> {
  const base =
    (data.fileName ?? 'Import').replace(/\.pptx$/i, '') || 'Import';
  const gap = 120;

  let mastersPage = tryCreateNamedPage(`01 PPT Masters · ${base}`);
  const createdMastersPage = mastersPage != null;
  if (!mastersPage) {
    mastersPage = figma.currentPage;
  }
  const masterBandTop = createdMastersPage ? 0 : maxChildBottomY(mastersPage) + gap;

  let slidesPage = tryCreateNamedPage(`02 PPT Slides · ${base}`);
  const createdSlidesPage = slidesPage != null;
  if (!slidesPage) {
    slidesPage = mastersPage;
  }

  if (!createdMastersPage || !createdSlidesPage) {
    figma.notify(
      '파일 페이지 수 제한으로 마스터·슬라이드를 한 페이지에 이어 배치했습니다. (예: Figma Starter는 파일당 페이지 3개)',
      { timeout: 8000 },
    );
  }

  const w = data.width;
  const h = data.height;
  let cursorX = 0;
  let cursorY = masterBandTop;

  const masterComponents: ComponentNode[] = [];
  const layoutGrid: (ComponentNode | undefined)[][] = [];

  const masters = data.masters!;
  for (let mi = 0; mi < masters.length; mi++) {
    const m = masters[mi];
    const masterBackdrop = figma.createFrame();
    masterBackdrop.name = `(배경) Master · ${m.name}`;
    masterBackdrop.resize(Math.max(1, w), Math.max(1, h));
    masterBackdrop.x = cursorX;
    masterBackdrop.y = cursorY;
    masterBackdrop.clipsContent = false;
    applyFrameBackground(masterBackdrop, m);
    mastersPage.appendChild(masterBackdrop);

    const mc = figma.createComponent();
    mc.name = `Master / ${m.name}`;
    mc.clipsContent = false;
    mc.fills = [];
    mastersPage.appendChild(mc);
    mc.resize(Math.max(1, w), Math.max(1, h));
    mc.x = cursorX;
    mc.y = cursorY;
    cursorX += w + gap;
    for (const sh of m.shapes) {
      await renderShape(mc, sh);
    }
    masterComponents.push(mc);
  }

  cursorY += h + gap;
  cursorX = 0;

  for (let mi = 0; mi < masters.length; mi++) {
    const m = masters[mi];
    layoutGrid[mi] = [];
    for (let li = 0; li < m.layouts.length; li++) {
      const lay = m.layouts[li];
      const layoutBackdrop = figma.createFrame();
      layoutBackdrop.name = `(배경) Layout · ${lay.name}`;
      layoutBackdrop.resize(Math.max(1, w), Math.max(1, h));
      layoutBackdrop.x = cursorX;
      layoutBackdrop.y = cursorY;
      layoutBackdrop.clipsContent = false;
      applyFrameBackground(layoutBackdrop, lay);
      mastersPage.appendChild(layoutBackdrop);

      const lc = figma.createComponent();
      lc.name = `Layout / ${m.name} / ${lay.name}`;
      lc.clipsContent = false;
      lc.fills = [];
      mastersPage.appendChild(lc);
      lc.resize(Math.max(1, w), Math.max(1, h));
      lc.x = cursorX;
      lc.y = cursorY;

      const minst = masterComponents[mi].createInstance();
      minst.x = 0;
      minst.y = 0;
      lc.appendChild(minst);
      for (const sh of lay.shapes) {
        await renderShape(lc, sh);
      }
      layoutGrid[mi][li] = lc;
      cursorX += w + gap;
    }
    cursorY += h + gap;
    cursorX = 0;
  }

  const slideYBase =
    slidesPage === mastersPage ? maxChildBottomY(mastersPage) + gap * 2 : 0;

  const slideFrames: FrameNode[] = [];
  let slideOffsetX = 0;
  for (const slide of data.slides) {
    const frame = figma.createFrame();
    frame.name = `Slide ${slide.slideNumber}`;
    frame.clipsContent = false;
    slidesPage.appendChild(frame);
    frame.resize(Math.max(1, w), Math.max(1, h));
    frame.x = slideOffsetX;
    frame.y = slideYBase;
    slideOffsetX += w + gap;
    applyFrameBackground(frame, slide);

    const mi = slide.masterIndex ?? -1;
    const li = slide.layoutIndex ?? -1;
    if (mi >= 0 && li >= 0 && layoutGrid[mi]?.[li]) {
      const inst = layoutGrid[mi][li]!.createInstance();
      inst.x = 0;
      inst.y = 0;
      frame.appendChild(inst);
    }
    for (const sh of slide.shapes) {
      await renderShape(frame, sh);
    }
    slideFrames.push(frame);
  }

  figma.currentPage = slidesPage;
  return slideFrames;
}

// ---------------------------------------------------------------------------
// Slide rendering (마스터 데이터 없을 때 기존 동작)
// ---------------------------------------------------------------------------

async function renderSlides(data: PptxData): Promise<SceneNode[]> {
  const frames: SceneNode[] = [];
  let offsetX = 0;
  const gap = 100;

  for (const slide of data.slides) {
    const frame = figma.createFrame();
    frame.name = `Slide ${slide.slideNumber}`;
    frame.clipsContent = false;
    frame.resize(data.width, data.height);
    frame.x = offsetX;
    frame.y = 0;
    offsetX += data.width + gap;
    applyFrameBackground(frame, slide);

    for (const shapeData of slide.shapes) {
      await renderShape(frame, shapeData);
    }
    figma.currentPage.appendChild(frame);
    frames.push(frame);
  }
  return frames;
}

type CreatedShape = RectangleNode | EllipseNode | VectorNode;

async function renderShape(parent: FrameNode | ComponentNode, shapeData: ShapeData): Promise<void> {
  if (shapeData.type === 'GROUP') {
    const frame = figma.createFrame();
    frame.name = 'Group';
    frame.clipsContent = false;
    frame.resize(Math.max(1, shapeData.width), Math.max(1, shapeData.height));
    frame.x = shapeData.x;
    frame.y = shapeData.y;
    frame.fills = [];
    parent.appendChild(frame);
    snapNodeCenterToPptBoundingBox(frame, shapeData, parent);
    for (const ch of shapeData.children ?? []) {
      await renderShape(frame, ch);
    }
    return;
  }

  if (shapeData.type === 'PICTURE') {
    const picNode = createPictureRectangle(shapeData);
    parent.appendChild(picNode);
    snapNodeCenterToPptBoundingBox(picNode, shapeData, parent);
    return;
  }

  const shapeNode = createShapeNode(shapeData);
  let textNode: TextNode | null = null;

  if (shapeData.text && shapeData.text.content && shapeData.text.content.length > 0) {
    textNode = await createTextNode(shapeData);
  }

  if (shapeNode && textNode) {
    parent.appendChild(shapeNode);
    parent.appendChild(textNode);
    const group = figma.group([shapeNode, textNode], parent);
    group.name = shapeNode.name;
    applyRotation(group, shapeData);
    return;
  }

  if (shapeNode) {
    parent.appendChild(shapeNode);
    snapNodeCenterToPptBoundingBox(shapeNode, shapeData, parent);
    return;
  }

  if (textNode) {
    parent.appendChild(textNode);
    snapNodeCenterToPptBoundingBox(textNode, shapeData, parent);
  }
}

// ---------------------------------------------------------------------------
// Shape factory
// ---------------------------------------------------------------------------

function createShapeNode(shapeData: ShapeData): CreatedShape | null {
  switch (shapeData.type) {
    case 'RECT':
    case 'TEXT_BOX':
      return createRectangle(shapeData);
    case 'ELLIPSE':
      return createEllipse(shapeData);
    case 'TRIANGLE':
      return createTriangle(shapeData);
    case 'LEFT_ARROW':
    case 'RIGHT_ARROW':
      return createHorizontalArrow(shapeData);
    case 'QUAD_ARROW':
      return createQuadArrow(shapeData);
    default:
      return null;
  }
}

function createRectangle(shapeData: ShapeData): RectangleNode {
  const rect = figma.createRectangle();
  rect.name = 'Rectangle';
  rect.resize(Math.max(1, shapeData.width), Math.max(1, shapeData.height));
  rect.x = shapeData.x;
  rect.y = shapeData.y;

  if (!tryApplyImageFill(rect, shapeData)) {
    if (shapeData.fillHex) {
      rect.fills = [{ type: 'SOLID', color: hexToRgb(shapeData.fillHex) }];
    } else {
      rect.fills = [];
    }
  }
  applyStroke(rect, shapeData);
  return rect;
}

function createEllipse(shapeData: ShapeData): EllipseNode {
  const ellipse = figma.createEllipse();
  ellipse.name = 'Ellipse';
  ellipse.resize(Math.max(1, shapeData.width), Math.max(1, shapeData.height));
  ellipse.x = shapeData.x;
  ellipse.y = shapeData.y;

  if (!tryApplyImageFill(ellipse, shapeData)) {
    if (shapeData.fillHex) {
      ellipse.fills = [{ type: 'SOLID', color: hexToRgb(shapeData.fillHex) }];
    }
  }
  applyStroke(ellipse, shapeData);
  return ellipse;
}

function createTriangle(shapeData: ShapeData): VectorNode {
  const triangle = figma.createVector();
  triangle.name = 'Triangle';
  const w = shapeData.width;
  const h = shapeData.height;
  triangle.vectorPaths = [
    { windingRule: 'NONZERO', data: `M ${w / 2} 0 L ${w} ${h} L 0 ${h} Z` },
  ];
  triangle.x = shapeData.x;
  triangle.y = shapeData.y;
  if (!tryApplyImageFill(triangle, shapeData)) {
    if (shapeData.fillHex) {
      triangle.fills = [{ type: 'SOLID', color: hexToRgb(shapeData.fillHex) }];
    }
  }
  applyStroke(triangle, shapeData);
  return triangle;
}

/**
 * PPT: LEFT_ARROW 또는 RIGHT_ARROW+flipH 는 왼쪽을 가리키며, 경로는 오른쪽 기준 후 X 미러.
 */
function shouldMirrorHorizontalArrow(shapeData: ShapeData): boolean {
  const left = shapeData.type === 'LEFT_ARROW';
  const fh = !!shapeData.flipHorizontal;
  return left !== fh;
}

function createHorizontalArrow(shapeData: ShapeData): VectorNode {
  const arrow = figma.createVector();
  arrow.name = shapeData.type === 'LEFT_ARROW' ? 'Left Arrow' : 'Right Arrow';
  const w = Math.max(1, shapeData.width);
  const h = Math.max(1, shapeData.height);
  /** DrawingML rightArrow: adj1=body half-height ratio, adj2=head depth from ss */
  const adj1Raw = shapeData.adjustValues?.[0] ?? 50000;
  const adj2Raw = shapeData.adjustValues?.[1] ?? 50000;
  const ss = Math.min(w, h);
  const hd2 = h / 2;
  const maxAdj2 = (100000 * w) / ss;
  const a1 = Math.max(0, Math.min(100000, adj1Raw));
  const a2 = Math.max(0, Math.min(maxAdj2, adj2Raw));
  const dx1 = (ss * a2) / 100000;
  const x1 = w - dx1;
  const dy1 = (h * a1) / 200000;
  const y1 = hd2 - dy1;
  const y2 = hd2 + dy1;
  const vc = hd2;
  const mirror = shouldMirrorHorizontalArrow(shapeData);
  const mx = (x: number) => (mirror ? w - x : x);
  const path =
    `M ${mx(0)} ${y1} L ${mx(x1)} ${y1} L ${mx(x1)} 0 L ${mx(w)} ${vc} L ${mx(x1)} ${h} L ${mx(x1)} ${y2} L ${mx(0)} ${y2} Z`;
  arrow.vectorPaths = [{ windingRule: 'NONZERO', data: path }];
  arrow.x = shapeData.x;
  arrow.y = shapeData.y;
  if (!tryApplyImageFill(arrow, shapeData)) {
    if (shapeData.fillHex) {
      arrow.fills = [{ type: 'SOLID', color: hexToRgb(shapeData.fillHex) }];
    }
  }
  applyStroke(arrow, shapeData);
  return arrow;
}

/** OOXML `quadArrow` 근사: 단일 단순 폐곡선(오목 덴트 없음). 덴트는 머리 밑이 V로 파여 보이고 틈처럼 렌더링되는 경우가 있음. */
function createQuadArrow(shapeData: ShapeData): VectorNode {
  const arrow = figma.createVector();
  arrow.name = 'Quad Arrow';
  const w = Math.max(1, shapeData.width);
  const h = Math.max(1, shapeData.height);
  const cx = w / 2;
  const cy = h / 2;
  const S = Math.min(w, h);

  const adj1Raw = shapeData.adjustValues?.[0] ?? 22500;
  const adj2Raw = shapeData.adjustValues?.[1] ?? 22500;
  const a1 = Math.max(0, Math.min(100000, adj1Raw)) / 100000;
  const a2 = Math.max(0, Math.min(100000, adj2Raw)) / 100000;

  const tip = Math.max(0.5, S * 0.012);
  let sh = S * (0.072 + 0.088 * a1);
  let hw = S * (0.2 + 0.075 * (1 - a1) + 0.045 * (1 - a2));
  hw = Math.max(hw, sh * 1.48);
  const hwSide = hw * (1.22 + 0.08 * (1 - a1));

  let hlV = S * (0.21 + 0.12 * a2);
  let hlH = S * (0.21 + 0.12 * a2);
  hlV = Math.min(hlV, cy - sh - tip - 0.5);
  hlH = Math.min(hlH, cx - sh - tip - 0.5);
  hlV = Math.max(S * 0.15, hlV);
  hlH = Math.max(S * 0.15, hlH);

  sh = Math.min(sh, hlV * 0.42, hlH * 0.42, cx - hw - 1, cy - hwSide - 1, 0.36 * S);

  const xr = w - tip;
  const yb = h - tip;
  const rxIn = w - hlH;
  const lxOut = hlH;
  const yt = tip;
  const yNeckT = hlV;
  const yNeckB = h - hlV;

  const P = (x: number, y: number) => `${x} ${y}`;

  const path = [
    `M ${P(cx, yt)}`,
    `L ${P(cx + hw, yNeckT)}`,
    `L ${P(cx + sh, yNeckT)}`,
    `L ${P(cx + sh, cy - sh)}`,
    `L ${P(rxIn, cy - sh)}`,
    `L ${P(rxIn, cy - hwSide)}`,
    `L ${P(xr, cy)}`,
    `L ${P(rxIn, cy + hwSide)}`,
    `L ${P(rxIn, cy + sh)}`,
    `L ${P(cx + sh, cy + sh)}`,
    `L ${P(cx + sh, yNeckB)}`,
    `L ${P(cx + hw, yNeckB)}`,
    `L ${P(cx, yb)}`,
    `L ${P(cx - hw, yNeckB)}`,
    `L ${P(cx - sh, yNeckB)}`,
    `L ${P(cx - sh, cy + sh)}`,
    `L ${P(lxOut, cy + sh)}`,
    `L ${P(lxOut, cy + hwSide)}`,
    `L ${P(tip, cy)}`,
    `L ${P(lxOut, cy - hwSide)}`,
    `L ${P(lxOut, cy - sh)}`,
    `L ${P(cx - sh, cy - sh)}`,
    `L ${P(cx - sh, yNeckT)}`,
    `L ${P(cx - hw, yNeckT)}`,
    'Z',
  ].join(' ');

  arrow.vectorPaths = [{ windingRule: 'NONZERO', data: path }];
  arrow.x = shapeData.x;
  arrow.y = shapeData.y;
  if (!tryApplyImageFill(arrow, shapeData)) {
    if (shapeData.fillHex) {
      arrow.fills = [{ type: 'SOLID', color: hexToRgb(shapeData.fillHex) }];
    }
  }
  applyStroke(arrow, shapeData);
  return arrow;
}

/** Base64 → IMAGE fill. 실패·누락이면 false (호출부에서 단색 등 처리). */
function tryApplyImageFill(node: GeometryMixin, shapeData: ShapeData): boolean {
  const b64 = shapeData.imageBase64;
  if (!b64 || b64.length === 0) return false;
  try {
    const binary = atob(b64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    const img = figma.createImage(bytes);
    node.fills = [{ type: 'IMAGE', scaleMode: 'FILL', imageHash: img.hash }];
    return true;
  } catch {
    return false;
  }
}

/** 삽입된 그림 → 사각형 + IMAGE fill (scaleMode FILL). Base64 없으면 회색 플레이스홀더. */
function createPictureRectangle(shapeData: ShapeData): RectangleNode {
  const rect = figma.createRectangle();
  rect.name = 'Picture';
  rect.resize(Math.max(1, shapeData.width), Math.max(1, shapeData.height));
  rect.x = shapeData.x;
  rect.y = shapeData.y;

  if (!tryApplyImageFill(rect, shapeData)) {
    rect.fills = [{ type: 'SOLID', color: { r: 0.85, g: 0.85, b: 0.85 } }];
  }
  applyStroke(rect, shapeData);
  return rect;
}

function applyStroke(node: GeometryMixin & MinimalStrokesMixin, shapeData: ShapeData): void {
  if (!shapeData.strokeHex) return;
  node.strokes = [{ type: 'SOLID', color: hexToRgb(shapeData.strokeHex) }];
  node.strokeWeight = shapeData.strokeWeight && shapeData.strokeWeight > 0 ? shapeData.strokeWeight : 1;
  applyDashStyle(node, shapeData);
}

// ---------------------------------------------------------------------------
// Text node creation
// ---------------------------------------------------------------------------

async function createTextNode(shapeData: ShapeData): Promise<TextNode> {
  const textData = shapeData.text!;
  const paragraphs = (textData.paragraphs && textData.paragraphs.length > 0)
    ? textData.paragraphs
    : [{ align: textData.align, runs: [{ text: textData.content ?? '', fontSize: textData.fontSize, colorHex: textData.colorHex }] }];

  // 첫 run의 폰트로 노드를 초기화한 뒤 character를 채우고 range 스타일 적용
  const firstRun = paragraphs[0]?.runs[0] ?? { text: '' };
  const firstFont = await resolveFont(firstRun.fontFamily, fontStyleOf(firstRun));

  const textNode = figma.createText();
  textNode.fontName = firstFont;
  textNode.x = shapeData.x;
  textNode.y = shapeData.y;

  // 단락 사이에 줄바꿈을 끼워 character 시퀀스를 만든다.
  let characters = '';
  type RangeSpec = { start: number; end: number; run: TextRun };
  const ranges: RangeSpec[] = [];

  for (let p = 0; p < paragraphs.length; p++) {
    const paragraph = paragraphs[p];
    if (p > 0) {
      characters += '\n';
    }
    for (const run of paragraph.runs) {
      const start = characters.length;
      characters += run.text ?? '';
      const end = characters.length;
      if (end > start) {
        ranges.push({ start, end, run });
      }
    }
  }

  if (characters.length === 0) {
    characters = textData.content ?? '';
  }
  textNode.characters = characters;
  textNode.resize(Math.max(1, shapeData.width), Math.max(1, shapeData.height));

  // 단락별 정렬 적용
  // Figma는 텍스트 노드 단위로 textAlignHorizontal 하나만 가지므로
  // 첫 단락의 정렬을 대표값으로 사용한다.
  const firstAlign = paragraphs[0]?.align ?? textData.align;
  if (firstAlign) {
    textNode.textAlignHorizontal = mapHorizontalAlign(firstAlign);
  }

  for (const range of ranges) {
    const { start, end, run } = range;
    const family = run.fontFamily?.trim() || DEFAULT_FONT_FAMILY;
    const style = fontStyleOf(run);
    const font = await resolveFont(family, style);
    try {
      textNode.setRangeFontName(start, end, font);
    } catch {
      // 무시
    }
    if (run.fontSize && run.fontSize > 0) {
      try {
        textNode.setRangeFontSize(start, end, run.fontSize);
      } catch {
        // 무시
      }
    }
    if (run.colorHex) {
      try {
        textNode.setRangeFills(start, end, [{ type: 'SOLID', color: hexToRgb(run.colorHex) }]);
      } catch {
        // 무시
      }
    }
    if (run.underline) {
      try {
        textNode.setRangeTextDecoration(start, end, 'UNDERLINE');
      } catch {
        // 무시
      }
    }
  }

  return textNode;
}

function mapHorizontalAlign(align: string): 'LEFT' | 'CENTER' | 'RIGHT' | 'JUSTIFIED' {
  switch (align) {
    case 'CENTER':
      return 'CENTER';
    case 'RIGHT':
      return 'RIGHT';
    case 'JUSTIFY':
      return 'JUSTIFIED';
    default:
      return 'LEFT';
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function hexToRgb(hex: string): RGB {
  const r = parseInt(hex.slice(1, 3), 16) / 255;
  const g = parseInt(hex.slice(3, 5), 16) / 255;
  const b = parseInt(hex.slice(5, 7), 16) / 255;
  return { r, g, b };
}

function applyFrameBackground(
  node: FrameNode | ComponentNode,
  bg: {
    backgroundFillHex?: string;
    backgroundImageBase64?: string;
    backgroundImageMimeType?: string;
  },
): void {
  const b64 = bg.backgroundImageBase64;
  if (b64 && b64.length > 0) {
    try {
      const binary = atob(b64);
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
      }
      const img = figma.createImage(bytes);
      node.fills = [{ type: 'IMAGE', scaleMode: 'FILL', imageHash: img.hash }];
      return;
    } catch {
      /* 단색으로 폴백 */
    }
  }
  applySolidBackground(node, bg.backgroundFillHex);
}

function applySolidBackground(node: FrameNode | ComponentNode, hex: string | undefined): void {
  if (!hex || hex.length < 7 || hex[0] !== '#') return;
  node.fills = [{ type: 'SOLID', color: hexToRgb(hex) }];
}

/**
 * PPT OOXML rot → Figma rotation.
 * - 화면 Y-down 기준으로 POI 각도에 -1을 곱하면 PPT와 맞는 경우가 많음 (삼각형 preset도 apex-top으로 동일).
 * - flipH XOR flipV: 미러+회전과 동일한 효과로 부호를 한 번 더 뒤집음.
 */
function figmaRotationFromPpt(shapeData: ShapeData): number {
  const deg = shapeData.rotation;
  const xorFlip = !!shapeData.flipHorizontal !== !!shapeData.flipVertical;
  let r = -deg;
  if (xorFlip) {
    r = -r;
  }
  return r;
}

function applyRotation(node: LayoutMixin, shapeData: ShapeData): void {
  const r = figmaRotationFromPpt(shapeData);
  if (Math.abs(r) > 1e-6) {
    node.rotation = r;
  }
}

/**
 * PPT off/ext(회전 전 박스)의 중심 (x+w/2, y+h/2)에 맞춘다.
 * Figma는 rotation 시 피벗이 좌상단에 가까워 회전 도형이 어긋나 보일 수 있다.
 */
function snapNodeCenterToPptBoundingBox(
  node: SceneNode & LayoutMixin,
  shapeData: ShapeData,
  parent: FrameNode | ComponentNode,
): void {
  const w = Math.max(1, shapeData.width);
  const h = Math.max(1, shapeData.height);
  const { x, y } = shapeData;
  const targetCx = x + w / 2;
  const targetCy = y + h / 2;

  node.x = x;
  node.y = y;

  const r = figmaRotationFromPpt(shapeData);
  if (Math.abs(r) > 1e-6) {
    node.rotation = r;
  } else {
    node.rotation = 0;
  }

  const pb = parent.absoluteBoundingBox;
  const nb = node.absoluteBoundingBox;
  if (!pb || !nb) return;

  const acx = nb.x + nb.width / 2 - pb.x;
  const acy = nb.y + nb.height / 2 - pb.y;
  node.x += targetCx - acx;
  node.y += targetCy - acy;
}

function dashMultipliersForStyleName(style: string): number[] {
  switch (style) {
    case 'DOT':
    case 'SYS_DOT':
      return [1, 1];
    case 'DASH':
      return [3, 4];
    case 'SYS_DASH':
      return [2, 2];
    case 'LG_DASH':
      return [8, 3];
    case 'DASH_DOT':
      return [4, 3, 1, 3];
    case 'LG_DASH_DOT':
      return [8, 3, 1, 3];
    case 'LG_DASH_DOT_DOT':
      return [8, 3, 1, 3, 1, 3];
    case 'SYS_DASH_DOT':
      return [2, 2, 1, 1];
    case 'SYS_DASH_DOT_DOT':
      return [2, 2, 1, 1, 1, 1];
    default:
      return [];
  }
}

function applyDashStyle(node: GeometryMixin, shapeData: ShapeData): void {
  const w =
    shapeData.strokeWeight && shapeData.strokeWeight > 0 ? shapeData.strokeWeight : 1;

  let mults = shapeData.dashPatternMultipliers;
  if (!mults || mults.length === 0) {
    const style = shapeData.dashStyle;
    if (!style || style === 'SOLID') return;
    mults = dashMultipliersForStyleName(style);
  }
  if (!mults || mults.length === 0) return;

  node.dashPattern = mults.map((m) => Math.max(1, m * w));
}
