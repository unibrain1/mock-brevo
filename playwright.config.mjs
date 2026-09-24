import { defineConfig, devices } from '@playwright/test';

// Runs against an already-started mock-brevo (CI starts the packaged jar).
// Locally: `docker compose up -d` with MOCK_BREVO_PORT, or `./mvnw spring-boot:run`,
// then `MOCK_BREVO_URL=http://localhost:18080 npm run test:ui`.
export default defineConfig({
  testDir: 'tests/ui',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.MOCK_BREVO_URL || 'http://localhost:8080',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
