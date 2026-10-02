import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { configDefaults } from 'vitest/config'

// The backend the dev server proxies to: ./infra/scripts/dev-up.sh (or bootRun) serves it on 8080.
const backend = process.env.API_TARGET ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    // The session screen's charts (ECharts, about 560 kB) and its map (MapLibre, about 1 MB) are chunks of their own,
    // loaded on demand; the rest of the app stays small.
    chunkSizeWarningLimit: 1100,
  },
  // MapLibre's worker is an ES module importing a shared chunk.
  worker: {
    format: 'es',
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
    // e2e/ holds the Playwright smoke test, run by infra/scripts/e2e.sh.
    exclude: [...configDefaults.exclude, 'e2e/**'],
    setupFiles: ['./src/test/setup.ts'],
  },
})
