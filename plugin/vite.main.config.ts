import { defineConfig } from 'vite';
import path from 'path';

export default defineConfig({
  build: {
    target: 'esnext',
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
