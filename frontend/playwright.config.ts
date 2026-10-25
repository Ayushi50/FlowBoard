import { defineConfig } from '@playwright/test'

/**
 * End-to-end tests run against a real stack (frontend + Spring Boot + PostgreSQL).
 * Start it first: `docker compose up -d --build` (frontend on :3000) or the dev servers (frontend on :5173).
 * Point E2E_BASE_URL at whichever you use. Set E2E_CHROME=1 to use an installed Google Chrome instead of
 * the Playwright-managed Chromium download.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    channel: process.env.E2E_CHROME ? 'chrome' : undefined,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
