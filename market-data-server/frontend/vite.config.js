import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8084',
        changeOrigin: true
      },
      '/actuator': {
        target: 'http://localhost:8084',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://localhost:8084',
        ws: true,
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true
  }
})