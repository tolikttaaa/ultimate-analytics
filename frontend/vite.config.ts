/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The backend the dev server proxies to: ./infra/scripts/dev-up.sh (or bootRun) serves it on 8080.
const backend = process.env.API_TARGET ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    // The session screen is its own chunk of about 560 kB, nearly all ECharts; the rest of the app stays small.
    chunkSizeWarningLimit: 600,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': backend,
      '/v3/api-docs': backend,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
