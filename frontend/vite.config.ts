import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],

  base: '/user/',
  resolve: {
  alias: {
      '@': path.resolve(__dirname, './src')

    },
  },
  define: {
    global: 'globalThis', // 또는 'window'
  },
})
