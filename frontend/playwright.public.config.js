import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests-public',
  fullyParallel: false,
  retries: 1,
  workers: 1,
  timeout: 45_000,
  expect: { timeout: 12_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'https://factoryflow.kro.kr',
    browserName: 'chromium',
    channel: 'chrome',
    viewport: { width: 1440, height: 900 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    navigationTimeout: 20_000,
  },
})
