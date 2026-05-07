figma.showUI(__html__, { width: 400, height: 300 });

figma.ui.onmessage = async (msg) => {
  if (msg.type === 'analyze-complete') {
    const { data } = msg.data; // PptxDataDTO
    if (!data) return;

    figma.notify(`${data.fileName} 변환을 시작합니다...`);

    const nodes: SceneNode[] = [];
    let offsetX = 0;

    for (const slide of data.slides) {
      // 1. 슬라이드 Frame 생성
      const frame = figma.createFrame();
      frame.name = `Slide ${slide.slideNumber}`;
      frame.resize(data.width, data.height);
      frame.x = offsetX;
      frame.y = 0;
      
      offsetX += data.width + 100; // 슬라이드 간 간격

      for (const shapeData of slide.shapes) {
        let node: SceneNode | null = null;

        if (shapeData.type === 'RECT' || shapeData.type === 'TEXT_BOX') {
          const rect = figma.createRectangle();
          rect.name = "Rectangle";
          rect.resize(shapeData.width, shapeData.height);
          rect.x = shapeData.x;
          rect.y = shapeData.y;
          
          if (shapeData.fillHex) {
            rect.fills = [{ type: 'SOLID', color: hexToRgb(shapeData.fillHex) }];
          } else {
            rect.fills = []; // 텍스트만 있는 박스인 경우 투명하게
          }

          // 테두리(Stroke) 적용
          if (shapeData.strokeHex) {
            rect.strokes = [{ type: 'SOLID', color: hexToRgb(shapeData.strokeHex) }];
            rect.strokeWeight = shapeData.strokeWeight > 0 ? shapeData.strokeWeight : 1;
            applyDashStyle(rect, shapeData.dashStyle);
          }

          node = rect;
        } else if (shapeData.type === 'ELLIPSE') {
          const ellipse = figma.createEllipse();
          ellipse.name = "Ellipse";
          ellipse.resize(shapeData.width, shapeData.height);
          ellipse.x = shapeData.x;
          ellipse.y = shapeData.y;
          
          if (shapeData.fillHex) {
            ellipse.fills = [{ type: 'SOLID', color: hexToRgb(shapeData.fillHex) }];
          }

          // 테두리(Stroke) 적용
          if (shapeData.strokeHex) {
            ellipse.strokes = [{ type: 'SOLID', color: hexToRgb(shapeData.strokeHex) }];
            ellipse.strokeWeight = shapeData.strokeWeight > 0 ? shapeData.strokeWeight : 1;
            applyDashStyle(ellipse, shapeData.dashStyle);
          }

          node = ellipse;
        } else if (shapeData.type === 'TRIANGLE') {
          const triangle = figma.createVector();
          triangle.name = "Triangle";
          const w = shapeData.width;
          const h = shapeData.height;
          
          // 이등변 삼각형 경로 생성
          triangle.vectorPaths = [{
            windingRule: "NONZERO",
            data: `M ${w/2} 0 L ${w} ${h} L 0 ${h} Z`
          }];
          
          triangle.x = shapeData.x;
          triangle.y = shapeData.y;
          
          if (shapeData.fillHex) {
            triangle.fills = [{ type: 'SOLID', color: hexToRgb(shapeData.fillHex) }];
          }
          if (shapeData.strokeHex) {
            triangle.strokes = [{ type: 'SOLID', color: hexToRgb(shapeData.strokeHex) }];
            triangle.strokeWeight = shapeData.strokeWeight > 0 ? shapeData.strokeWeight : 1;
            applyDashStyle(triangle, shapeData.dashStyle);
          }
          node = triangle;
        } else if (shapeData.type === 'RIGHT_ARROW') {
          const arrow = figma.createVector();
          arrow.name = "Right Arrow";
          const w = shapeData.width;
          const h = shapeData.height;
          
          // PPT 조정값 적용 (0-100000 범위를 0-1로 변환)
          // adj1: 화살표 머리 너비, adj2: 화살표 몸통 두께
          const adj1 = (shapeData.adjustValues && shapeData.adjustValues[0] !== undefined) ? shapeData.adjustValues[0] / 100000 : 0.25;
          const adj2 = (shapeData.adjustValues && shapeData.adjustValues[1] !== undefined) ? shapeData.adjustValues[1] / 100000 : 0.5;

          const headStart = w * (1 - adj1);
          const bodyThickness = h * adj2;
          const bodyTop = (h - bodyThickness) / 2;
          const bodyBottom = (h + bodyThickness) / 2;

          const path = `M 0 ${bodyTop} L ${headStart} ${bodyTop} L ${headStart} 0 L ${w} ${h*0.5} L ${headStart} ${h} L ${headStart} ${bodyBottom} L 0 ${bodyBottom} Z`;
          
          arrow.vectorPaths = [{
            windingRule: "NONZERO",
            data: path
          }];
          arrow.x = shapeData.x;
          arrow.y = shapeData.y;

          if (shapeData.fillHex) {
            arrow.fills = [{ type: 'SOLID', color: hexToRgb(shapeData.fillHex) }];
          }
          if (shapeData.strokeHex) {
            arrow.strokes = [{ type: 'SOLID', color: hexToRgb(shapeData.strokeHex) }];
            arrow.strokeWeight = shapeData.strokeWeight > 0 ? shapeData.strokeWeight : 1;
            applyDashStyle(arrow, shapeData.dashStyle);
          }
          node = arrow;
        }

        if (node) {
          // 공통 속성: 회전(Rotation) 적용
          if (shapeData.rotation !== 0) {
            node.rotation = shapeData.rotation;
          }
        }

        // 텍스트가 있는 경우 추가 생성
        if (shapeData.text) {
          const textNode = figma.createText();
          
          // 폰트 로드 (기본 폰트 사용)
          await figma.loadFontAsync({ family: "Inter", style: "Regular" });
          
          textNode.characters = shapeData.text.content;
          textNode.fontSize = shapeData.text.fontSize > 0 ? shapeData.text.fontSize : 12;
          textNode.x = shapeData.x;
          textNode.y = shapeData.y;
          textNode.resize(shapeData.width, shapeData.height);
          
          if (shapeData.text.colorHex) {
            textNode.fills = [{ type: 'SOLID', color: hexToRgb(shapeData.text.colorHex) }];
          }

          // 텍스트 정렬 설정
          if (shapeData.text.align === 'CENTER') {
            textNode.textAlignHorizontal = 'CENTER';
          } else if (shapeData.text.align === 'RIGHT') {
            textNode.textAlignHorizontal = 'RIGHT';
          } else if (shapeData.text.align === 'JUSTIFY') {
            textNode.textAlignHorizontal = 'JUSTIFIED';
          }

          if (node) {
            frame.appendChild(node);
          }
          frame.appendChild(textNode);
        } else if (node) {
          frame.appendChild(node);
        }
      }
      nodes.push(frame);
    }

    figma.viewport.scrollAndZoomIntoView(nodes);
    figma.notify('변환이 완료되었습니다!');
  }
};

function hexToRgb(hex: string): RGB {
  const r = parseInt(hex.slice(1, 3), 16) / 255;
  const g = parseInt(hex.slice(3, 5), 16) / 255;
  const b = parseInt(hex.slice(5, 7), 16) / 255;
  return { r, g, b };
}

function applyDashStyle(node: GeometryMixin, style: string | undefined) {
  if (!style || style === 'SOLID') return;

  if (style === 'DASH' || style === 'SYS_DASH' || style === 'LG_DASH') {
    node.dashPattern = [4, 4];
  } else if (style === 'DOT' || style === 'SYS_DOT') {
    node.dashPattern = [1, 2];
  } else if (style === 'DASH_DOT' || style === 'SYS_DASH_DOT') {
    node.dashPattern = [4, 2, 1, 2];
  }
}
