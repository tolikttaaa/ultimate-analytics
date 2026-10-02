import { defineConfig, devices } from '@playwright/test'

/**
 * The Playwright smoke test (spec 12). It needs a running app: `../infra/scripts/e2e.sh` starts a fresh, isolated
 * stack, runs it and removes the stack again.
 */
export default defineConfig({
  testDir: 'e2e',
  timeout: 60_000,
  forbidOnly: Boolean(process.env.CI),
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:18081',
    // Desktop only (spec 10.3).
    viewport: { width: 1440, height: 1000 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 1000 } } }],
})
