import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

const backendPort = process.env.BACKEND_PORT || '8080'
const backendTarget = process.env.VITE_API_URL || process.env.BACKEND_URL || `http://127.0.0.1:${backendPort}`

export default defineConfig({
  plugins: [react()],
  server: {
    port: Number(process.env.PORT || process.env.FRONTEND_PORT || 5173),
    proxy: {
      '/api': {
        target: backendTarget,
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // Browser E2E lives in e2e/ under Playwright — never under Vitest/jsdom.
    exclude: ['**/node_modules/**', '**/dist/**', 'e2e/**'],
  },
})
