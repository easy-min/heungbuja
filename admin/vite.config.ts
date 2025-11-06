import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
    define: {
    // sockjs-client를 위한 global 객체 정의
    global: 'globalThis',
  },
})
