import { defineConfig, devices } from '@playwright/test'

/**
 * Screenshots of the user guide (docs/USER_GUIDE.md): `../infra/scripts/e2e.sh --config playwright.guide.config.ts`
 * fills a fresh stack with the golden sessions and saves annotated screenshots to docs/guide/.
 */
export default defineConfig({
  testDir: 'guide',
  timeout: 300_000,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:18081',
    colorScheme: 'light',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 940 } } }],
})
