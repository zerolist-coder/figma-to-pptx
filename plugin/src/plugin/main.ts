// ---------------------------------------------------------------------------
// Backend DTO와 1:1 매칭되는 타입 정의 (PptxDataDTO 등)
// ---------------------------------------------------------------------------

interface PptxData {
  fileName: string;
  width: number;
  height: number;
  slides: SlideData[];
}

interface SlideData {
  slideNumber: number;
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
    const payload = msg.data as { data: PptxData; buildLabel?: string };
    const data = payload.data;
    if (!data) return;

    const tag = payload.buildLabel ?? importBuildLabel();
    const fn = data.fileName ?? '(이름 없음)';

    figma.notify(`[${tag}] ${fn} 변환 시작…`);

    try {
      await preloadFonts(data);
      const nodes = await renderSlides(data);
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

  for (const slide of data.slides) {
    for (const shape of slide.shapes) {
      const text = shape.text;
      if (!text) continue;

      const paragraphs = text.paragraphs ?? [];
      if (paragraphs.length === 0) {
        enqueue(DEFAULT_FONT_FAMILY, 'Regular');
        continue;
      }
      for (const paragraph of paragraphs) {
        for (const run of paragraph.runs) {
          const family = run.fontFamily?.trim() || DEFAULT_FONT_FAMILY;
          enqueue(family, fontStyleOf(run));
        }
      }
    }
  }

  await Promise.all(tasks);
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
// Slide rendering
// ---------------------------------------------------------------------------

async function renderSlides(data: PptxData): Promise<SceneNode[]> {
  const frames: SceneNode[] = [];
  let offsetX = 0;
  const gap = 100;

  for (const slide of data.slides) {
    const frame = figma.createFrame();
    frame.name = `Slide ${slide.slideNumber}`;
    frame.resize(data.width, data.height);
    frame.x = offsetX;
    frame.y = 0;
    offsetX += data.width + gap;

    for (const shapeData of slide.shapes) {
      await renderShape(frame, shapeData);
    }
    frames.push(frame);
  }
  return frames;
}

type CreatedShape = RectangleNode | EllipseNode | VectorNode;

async function renderShape(parent: FrameNode, shapeData: ShapeData): Promise<void> {
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
    case 'RIGHT_ARROW':
      return createRightArrow(shapeData);
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

  if (shapeData.fillHex) {
    rect.fills = [{ type: 'SOLID', color: hexToRgb(shapeData.fillHex) }];
  } else {
    rect.fills = [];
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

  if (shapeData.fillHex) {
    ellipse.fills = [{ type: 'SOLID', color: hexToRgb(shapeData.fillHex) }];
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
  if (shapeData.fillHex) {
    triangle.fills = [{ type: 'SOLID', color: hexToRgb(shapeData.fillHex) }];
  }
  applyStroke(triangle, shapeData);
  return triangle;
}

function createRightArrow(shapeData: ShapeData): VectorNode {
  const arrow = figma.createVector();
  arrow.name = 'Right Arrow';
  const w = Math.max(1, shapeData.width);
  const h = Math.max(1, shapeData.height);
  /** DrawingML rightArrow (ISO preset): adj1=body half-height ratio, adj2=head depth from ss */
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
  const path = `M 0 ${y1} L ${x1} ${y1} L ${x1} 0 L ${w} ${vc} L ${x1} ${h} L ${x1} ${y2} L 0 ${y2} Z`;
  arrow.vectorPaths = [{ windingRule: 'NONZERO', data: path }];
  arrow.x = shapeData.x;
  arrow.y = shapeData.y;
  if (shapeData.fillHex) {
    arrow.fills = [{ type: 'SOLID', color: hexToRgb(shapeData.fillHex) }];
  }
  applyStroke(arrow, shapeData);
  return arrow;
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
  parent: FrameNode,
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
