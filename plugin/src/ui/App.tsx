import React, { useCallback, useEffect, useState } from 'react';

type AnalyzePayload = {
  data?: {
    fileName?: string;
    usedFontFamilies?: string[];
    slides?: unknown[];
  };
  message?: string;
  error?: string;
};

const App: React.FC = () => {
  const [status, setStatus] = useState<string>('업로드 대기 중');
  const [usedFonts, setUsedFonts] = useState<string[]>([]);
  const [pendingPayload, setPendingPayload] = useState<AnalyzePayload | null>(null);
  const [conversionSent, setConversionSent] = useState(false);

  const resetUploadState = useCallback(() => {
    setUsedFonts([]);
    setPendingPayload(null);
    setConversionSent(false);
  }, []);

  useEffect(() => {
    const onPluginMessage = (event: MessageEvent) => {
      const pm = event.data?.pluginMessage;
      if (pm?.type === 'convert-finished') {
        if (pm.ok) {
          setStatus('변환이 완료되었습니다. Figma 캔버스를 확인하세요. 다른 파일은 다시 업로드하세요.');
        } else {
          setStatus(`변환 실패: ${pm.error ?? '알 수 없는 오류'}`);
          setConversionSent(false);
        }
      }
    };
    window.addEventListener('message', onPluginMessage);
    return () => window.removeEventListener('message', onPluginMessage);
  }, []);

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const fileName = file.name;
    resetUploadState();

    setStatus(`${fileName} — 백엔드로 분석 중…`);

    const formData = new FormData();
    formData.append('file', file);

    try {
      const response = await fetch('http://localhost:3000/api/imports/analyze', {
        method: 'POST',
        body: formData,
      });

      const payload = (await response.json()) as AnalyzePayload;
      const serverName = payload?.data?.fileName ?? fileName;

      if (!response.ok) {
        throw new Error(payload.error ?? `Server responded with ${response.status}`);
      }

      const fonts = [...(payload.data?.usedFontFamilies ?? [])];
      setUsedFonts(fonts);
      setPendingPayload(payload);
      setStatus(
        fonts.length > 0
          ? `분석 완료: ${payload.message ?? ''} (File: ${serverName}) — 아래 폰트를 확인한 뒤 변환하세요.`
          : `분석 완료: ${payload.message ?? ''} (File: ${serverName}) — 텍스트에 폰트 정보가 없습니다. 바로 변환할 수 있습니다.`,
      );
    } catch (error: any) {
      setStatus(`오류: ${error.message}`);
      resetUploadState();
    } finally {
      event.target.value = '';
    }
  };

  const handleConvert = () => {
    if (!pendingPayload || conversionSent) return;
    setConversionSent(true);
    parent.postMessage({ pluginMessage: { type: 'convert-ppt', data: pendingPayload } }, '*');
    setStatus('Figma에서 변환 중… 잠시만 기다려 주세요.');
  };

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif' }}>
      <h2>PPT to Figma Converter</h2>
      <p>상태: {status}</p>
      <p style={{ fontSize: 12, color: '#666' }}>
        분석은 서버에서 수행됩니다. 폰트 확인 후 &quot;Figma에 변환&quot;을 누르면 캔버스에 노드가 만들어집니다.
      </p>
      <input type="file" accept=".pptx" onChange={handleFileUpload} />

      {pendingPayload != null && (
        <div style={{ marginTop: '16px' }}>
          <h3 style={{ fontSize: '14px', margin: '0 0 8px 0' }}>문서에서 사용된 폰트</h3>
          {usedFonts.length === 0 ? (
            <p style={{ fontSize: 13, color: '#555', margin: '8px 0' }}>
              (추출된 폰트 패밀리 없음 — 플레이스홀더만 있거나 텍스트에 글꼴 이름이 없을 수 있습니다.)
            </p>
          ) : (
            <ul
              style={{
                margin: '8px 0',
                paddingLeft: '20px',
                maxHeight: '160px',
                overflowY: 'auto',
                fontSize: 13,
              }}
            >
              {usedFonts.map((f) => (
                <li key={f}>{f}</li>
              ))}
            </ul>
          )}
          <button
            type="button"
            disabled={conversionSent}
            onClick={handleConvert}
            style={{
              marginTop: '8px',
              padding: '8px 14px',
              cursor: conversionSent ? 'default' : 'pointer',
              fontWeight: 600,
              opacity: conversionSent ? 0.6 : 1,
            }}
          >
            {conversionSent ? '변환 요청됨…' : 'Figma에 변환'}
          </button>
        </div>
      )}
    </div>
  );
};

export default App;
