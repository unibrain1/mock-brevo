import { test, expect } from '@playwright/test';

// Each run provisions its own tenant so assertions don't depend on prior data.
const API_KEY = `ui-test-${Date.now()}`;
let campaignId;

test.beforeAll(async ({ request }) => {
  const account = await request.get('/v3/account', { headers: { 'api-key': API_KEY } });
  expect(account.ok()).toBeTruthy();
  const campaigns = await request.get(`/mock-status/accounts/${API_KEY}/campaigns`);
  campaignId = (await campaigns.json()).campaigns[0].id;
});

test.describe('language detection', () => {
  test.use({ locale: 'en-US' });

  test('English browser gets the English UI', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page.locator('nav.tabs button[data-tab="accounts"]')).toHaveText('Accounts');
    await expect(page.locator('#langLabel')).toHaveText('EN');
    await expect(page.locator('#healthText')).toContainText('account(s)');
    await expect(page.locator('#accountsBody')).toContainText(API_KEY);
  });
});

test.describe('language detection (fr)', () => {
  test.use({ locale: 'fr-FR' });

  test('French browser gets the French UI', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
    await expect(page.locator('nav.tabs button[data-tab="accounts"]')).toHaveText('Comptes');
    await expect(page.locator('#healthText')).toContainText('compte(s)');
  });
});

test.describe('toggle', () => {
  test.use({ locale: 'en-US' });

  test('switches language in place and persists across reloads', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#resetAllBtn')).toHaveText('Reset everything');

    await page.locator('#langToggle').click();
    await expect(page.locator('#langLabel')).toHaveText('FR');
    await expect(page.locator('#resetAllBtn')).toHaveText('Tout réinitialiser');
    await expect(page.locator('#filter')).toHaveAttribute('placeholder', /Filtrer/);

    await page.reload();
    await expect(page.locator('#langLabel')).toHaveText('FR');
    expect(await page.evaluate(() => localStorage.getItem('mock-brevo-lang'))).toBe('fr');
  });

  test('reset confirmation is translated and can be cancelled', async ({ page }) => {
    await page.goto('/');
    let message = null;
    page.once('dialog', (d) => { message = d.message(); d.dismiss(); });
    await page.locator('#resetAllBtn').click();
    expect(message).toContain('Permanently delete ALL accounts');
    await expect(page.locator('#accountsBody')).toContainText(API_KEY);
  });

  test('open request details survive a language switch', async ({ page }) => {
    await page.goto('/');
    await page.locator('nav.tabs button[data-tab="requests"]').click();
    await page.locator('#filter').fill(API_KEY.slice(0, 6));
    const firstRow = page.locator('#requestsBody tr.summary').first();
    await firstRow.click();
    await expect(page.locator('#requestsBody .detail-inner h3').first()).toContainText('Request');

    await page.locator('#langToggle').click();
    await expect(page.locator('#requestsBody tr.summary.open')).toHaveCount(1);
    await expect(page.locator('#requestsBody .detail-inner h3').first()).toContainText('Requête');
  });
});

test.describe('deep links', () => {
  test.use({ locale: 'en-US' });

  test('campaign detail page renders and re-renders on toggle', async ({ page }) => {
    await page.goto(`/marketing-campaign/edit/${campaignId}`);
    await expect(page.locator('#deepLinkTitle')).toContainText('Campaign —');
    await expect(page.locator('.deep-card')).toContainText('Sender');

    await page.locator('#langToggle').click();
    await expect(page.locator('#deepLinkTitle')).toContainText('Campagne —');
    await expect(page.locator('.deep-card')).toContainText('Expéditeur');
  });

  test('unknown list shows a translated not-found message', async ({ page }) => {
    await page.goto('/contact/list/id/999999');
    await expect(page.locator('.deep-not-found')).toContainText('List #999999 not found');
  });
});
