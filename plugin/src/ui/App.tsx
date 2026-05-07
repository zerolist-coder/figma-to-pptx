import React, { useState } from 'react';

const App: React.FC = () => {
  const [status, setStatus] = useState<string>('업로드 대기 중');

  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    setStatus('백엔드로 파일 전송 중...');

    const formData = new FormData();
    formData.append('file', file);

    try {
      // PRD에 명시된 백엔드 로컬 주소로 전송
      const response = await fetch('http://localhost:3000/api/imports/analyze', {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        throw new Error(`Server responded with ${response.status}`);
      }

      const data = await response.json();
      setStatus(`분석 완료: ${data.message} (File: ${data.data.fileName})`);
      
      // 플러그인 백그라운드 로직(main.ts)으로 결과 메시지 전송 예시
      parent.postMessage({ pluginMessage: { type: 'analyze-complete', data } }, '*');

    } catch (error: any) {
      setStatus(`오류 발생: ${error.message}`);
    }
  };

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif' }}>
      <h2>PPT to Figma Converter</h2>
      <p>상태: {status}</p>
      <input type="file" accept=".pptx" onChange={handleFileUpload} />
    </div>
  );
};

export default App;
