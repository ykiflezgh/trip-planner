import { defineConfig, devices } from '@playwright/test'

// 5174, not the dev server's 5173: a plain `npm run dev` talks to the real project and must never be reused.
const PORT = 5174
const APP = `http://localhost:${PORT}/app/`

/**
 * Playwright suite against the Firebase Emulator Suite (Auth + Firestore), no production credentials:
 * `npm run e2e:emulators` (or `sh e2e/with-emulators.sh npm run e2e`). Specs are `*.e2e.ts` so Vitest's
 * default glob ignores them and tsconfig.app.json (`include: ["src"]`) keeps them out of `tsc -b`.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: /.*\.e2e\.ts$/,
  globalSetup: './e2e/global-setup.ts',
  fullyParallel: false,
  workers: 1, // one emulator, shared Auth users; trips are per test anyway
  retries: 1,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: APP, // page.goto('/app/t/x') stays absolute; relative paths resolve under /app/
    trace: 'on-first-retry',
    timezoneId: 'UTC', // deterministic zone toggle (trip is Europe/Rome) and day labels
    locale: 'en-US', // "Day 2 · Oct 6"
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `npx vite --port ${PORT} --strictPort --mode e2e`,
    url: APP,
    reuseExistingServer: false, // --strictPort makes a collision fail loudly instead of hitting production
    env: { VITE_USE_EMULATORS: '1' }, // process env has the highest precedence in Vite, belt and braces over .env.e2e
    timeout: 60_000,
  },
})
