import React, { useState } from 'react';
import { importBuildLabel } from '../constants/import-build';

const App: React.FC = () => {
  const [status, setStatus] = useState<string>('업로드 대기 중');

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const fileName = file.name;
    const fallbackTag = importBuildLabel();

    setStatus(`[${fallbackTag}] ${fileName} — 백엔드로 전송 중... (Eclipse 콘솔에 서버 로그 표시)`);

    const formData = new FormData();
    formData.append('file', file);

    try {
      const response = await fetch('http://localhost:3000/api/imports/analyze', {
        method: 'POST',
        body: formData,
      });

      const data = await response.json();
      const tag = (typeof data.buildLabel === 'string' ? data.buildLabel : null) ?? fallbackTag;
      const serverName = data?.data?.fileName ?? fileName;

      if (!response.ok) {
        throw new Error(data.error ?? `Server responded with ${response.status}`);
      }

      setStatus(`[${tag}] 분석 완료: ${data.message} (File: ${serverName})`);

      parent.postMessage({ pluginMessage: { type: 'analyze-complete', data } }, '*');
    } catch (error: any) {
      setStatus(`[${fallbackTag}] 오류: ${error.message}`);
    }
  };

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif' }}>
      <h2>PPT to Figma Converter</h2>
      <p>상태: {status}</p>
      <p style={{ fontSize: 12, color: '#666' }}>
        빌드 태그는 서버 기준입니다. 단계 로그는 <strong>Eclipse</strong>에서 Spring Boot 앱 콘솔(로그)을 확인하세요.
      </p>
      <input type="file" accept=".pptx" onChange={handleFileUpload} />
    </div>
  );
};

export default App;
