import { test, expect } from '@playwright/test';

// evaluate() callbacks run in the page.
/* global window, document */

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

test('header shows the build version', async ({ page, request }) => {
  const { version } = await (await request.get('/mock-status/version')).json();
  await page.goto('/');
  await expect(page.locator('#versionBadge')).toHaveText(`v${version}`);
  await expect(page.locator('#versionBadge')).toHaveAttribute('title', /upstream/);
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

test('request log links a call to its Brevo reference page', async ({ page, request }) => {
  expect((await request.get('/v3/senders', { headers: { 'api-key': API_KEY } })).ok()).toBeTruthy();
  await page.goto('/');
  await page.locator('nav.tabs button[data-tab="requests"]').click();
  const row = page.locator('#requestsBody tr.summary', { hasText: '/v3/senders' }).first();
  await expect(row.locator('a.doc-link')).toHaveAttribute('href', 'https://developers.brevo.com/reference/get-senders');
});

test('campaign detail page links to the current Brevo doc slug', async ({ page }) => {
  await page.goto(`/marketing-campaign/edit/${campaignId}`);
  await expect(page.locator('.deep-card a.doc-link'))
    .toHaveAttribute('href', 'https://developers.brevo.com/reference/get-email-campaign');
});

test.describe('block list', () => {
  test.use({ locale: 'en-US' });

  test('blocked counter opens a translated drill-down', async ({ page, request }) => {
    const seeded = await request.post(`/mock-status/accounts/${API_KEY}/blocked`,
      { data: { email: 'blocked-ui@example.com', reason: 'hardBounce' } });
    expect(seeded.status()).toBe(201);

    await page.goto('/');
    await page.locator('#filter').fill(API_KEY);
    await page.locator(`.counter-drill[data-drill="blocked"][data-api-key="${API_KEY}"]`).click();
    const drill = page.locator('.drill-wrap[data-kind="blocked"]');
    await expect(drill).toContainText('Blocked contacts (1)');
    await expect(drill).toContainText('blocked-ui@example.com');
    await expect(drill).toContainText('hardBounce');

    await page.locator('#langToggle').click();
    await expect(page.locator('.drill-wrap[data-kind="blocked"]')).toContainText('Contacts bloqués (1)');
  });
});

test.describe('sent emails', () => {
  test.use({ locale: 'en-US' });

  test('emailsSent counter lists the tags and opens a detail row', async ({ page, request }) => {
    const sent = await request.post('/v3/smtp/email', {
      headers: { 'api-key': API_KEY },
      data: {
        sender: { email: 'noreply@example.com' },
        to: [{ email: 'tags-ui@example.com' }],
        cc: [{ email: 'cc-ui@example.com' }],
        subject: 'Tag check',
        htmlContent: `<p>${'Hi there. '.repeat(400)}long-line-marker</p>`,
        tags: ['car_verification'],
      },
    });
    expect(sent.status()).toBe(201);
    const { messageId } = await sent.json();
    // A second email gives the drill-down a detail row that stays hidden.
    const other = await request.post('/v3/smtp/email', {
      headers: { 'api-key': API_KEY },
      data: { sender: { email: 'noreply@example.com' }, to: [{ email: 'other-ui@example.com' }], subject: 'Other mail' },
    });
    expect(other.status()).toBe(201);

    await page.goto('/');
    await page.locator('#filter').fill(API_KEY);
    await page.locator(`.counter-drill[data-drill="emails"][data-api-key="${API_KEY}"]`).click();
    const drill = page.locator('.drill-wrap[data-kind="emails"]');
    const row = drill.locator('tr.summary', { hasText: 'Tag check' });
    await expect(row).toContainText('tags-ui@example.com');
    await expect(row.locator('.email-tag')).toHaveText('car_verification');

    await row.click();
    const detail = drill.locator('tr.detail:visible');
    await expect(detail).toContainText(messageId);
    await expect(detail).toContainText('cc-ui@example.com');
    await expect(detail.locator('.email-tag')).toHaveText('car_verification');

    // A long line wraps: the box needs no horizontal scroll.
    const payload = detail.locator('pre.email-payload');
    await expect(payload).toContainText('long-line-marker');
    expect(await payload.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
    // Also inside the viewport: a cell that grows to the content would pass the check above.
    expect(await payload.evaluate(el => el.getBoundingClientRect().right <= document.documentElement.clientWidth)).toBe(true);

    // A hidden detail row is empty: the poll formats only the open rows.
    await expect(drill.locator('tr.detail[hidden]')).toHaveCount(1);
    await expect(drill.locator('tr.detail[hidden] .detail-inner')).toHaveCount(0);

    // A selection in the open row survives the 2s poll (the row is not rebuilt).
    await payload.evaluate(el => {
      el.dataset.marker = 'kept';
      const range = document.createRange();
      range.selectNodeContents(el);
      window.getSelection().removeAllRanges();
      window.getSelection().addRange(range);
    });
    await page.waitForTimeout(4500);
    expect(await page.evaluate(() => ({
      kept: document.querySelector('pre.email-payload')?.dataset.marker,
      selected: window.getSelection().toString().includes('long-line-marker'),
    }))).toEqual({ kept: 'kept', selected: true });
    await page.evaluate(() => window.getSelection().removeAllRanges());

    // The poll rebuilds the open row and keeps the scroll position of its box.
    const scrolled = await payload.evaluate(el => { el.scrollTop = 120; return el.scrollTop; });
    expect(scrolled).toBeGreaterThan(0);
    await page.waitForResponse(r => r.url().includes(`/accounts/${API_KEY}/emails`));
    await expect.poll(() => page.evaluate(() => {
      const pre = document.querySelector('pre.email-payload');
      return pre && !pre.dataset.marker ? pre.scrollTop : -1;
    })).toBe(scrolled);

    // The 2s poll rebuilds the drill-down. The open row must stay open.
    await page.waitForResponse(r => r.url().includes(`/accounts/${API_KEY}/emails`));
    await expect(drill.locator('tr.detail:visible')).toContainText(messageId);

    await page.locator('#langToggle').click();
    await expect(page.locator('.drill-wrap[data-kind="emails"]')).toContainText('Emails envoyés');
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
