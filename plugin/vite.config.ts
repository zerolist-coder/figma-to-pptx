import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { viteSingleFile } from 'vite-plugin-singlefile';
import path from 'path';

export default defineConfig({
  plugins: [react(), viteSingleFile()],
  build: {
    target: 'esnext',
    outDir: 'dist',
    emptyOutDir: false, // main 빌드 결과물이 지워지지 않도록 설정
    rollupOptions: {
      input: {
        ui: path.resolve(__dirname, 'src/ui/index.html')
      }
    },
  },
});
