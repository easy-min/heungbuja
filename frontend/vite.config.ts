import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  base: '/user/',  // ← /user 경로로 배포
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  }
})
