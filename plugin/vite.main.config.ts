import { defineConfig } from 'vite';
import path from 'path';

// Figma plugin main 스레드는 ES2020(?., ??) 미지원. es2017로 낮춰 esbuild가 하위 변환한다.
export default defineConfig({
  build: {
    target: 'es2017',
    outDir: 'dist',
    emptyOutDir: true, // 첫 빌드이므로 dist 폴더를 비웁니다.
    lib: {
      entry: path.resolve(__dirname, 'src/plugin/main.ts'),
      name: 'plugin',
      formats: ['iife'],
      fileName: () => 'main.js'
    },
  },
});
