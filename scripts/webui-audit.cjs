'use strict';

// Run in a trusted owner check. Keep Playwright and Chromium outside the product runtime.
const {mkdir, writeFile} = require('node:fs/promises');
const {resolve, join} = require('node:path');

function trustedOrigin(value) {
  const url = new URL(value);
  if (url.username || url.password || url.pathname !== '/' || url.search || url.hash
      || (url.protocol !== 'https:' && !/^http:\/\/(127\.0\.0\.1|\[::1\])(?::\d+)?\/?$/.test(value))) {
    throw new Error('Unsupported audit origin');
  }
  return url.origin;
}

function requestAllowed(url, origin, method, loginAvailable) {
  if (url.origin !== origin) return false;
  if (method === 'GET' || method === 'HEAD') return !url.pathname.startsWith('/api/v1/auth/logout');
  return loginAvailable && method === 'POST' && url.pathname === '/api/v1/auth/login' && !url.search;
}

const pages = ['home', 'overview', 'general-settings', 'vote-sites', 'rewards', 'configurations',
  'data', 'servers', 'network', 'activity', 'access'];

async function audit() {
  const origin = trustedOrigin(process.env.VP_CONTROL_AUDIT_ORIGIN);
  let password = process.env.VOTINGPLUGIN_CONTROL_WEBUI_PASSWORD;
  delete process.env.VOTINGPLUGIN_CONTROL_WEBUI_PASSWORD;
  for (const key of ['DEBUG', 'PWDEBUG', 'NODE_DEBUG']) delete process.env[key];
  if (!password) throw new Error('Audit credential unavailable');
  const output = resolve(process.env.VP_CONTROL_AUDIT_OUTPUT);
  // Captures of real data require page-specific redaction; keep screenshots fixture-only.
  const fixture = process.argv.includes('--synthetic-fixture');
  if (fixture && !origin.startsWith('http://127.0.0.1:')) throw new Error('Fixture must be local');
  await mkdir(output, {mode: 0o700});
  const {chromium} = require('playwright');
  const browser = await chromium.launch();
  try {
    const context = await browser.newContext({serviceWorkers: 'block', acceptDownloads: false});
    let loginAvailable = true;
    let blockedWrites = 0;
    await context.route('**/*', async route => {
      const request = route.request();
      const url = new URL(request.url());
      const method = request.method();
      if (!requestAllowed(url, origin, method, loginAvailable)) {
        if (method !== 'GET' && method !== 'HEAD') blockedWrites++;
        return route.abort();
      }
      if (method === 'POST') loginAvailable = false;
      try {
        const response = await route.fetch({maxRedirects: 0, maxRetries: 0, timeout: 8000});
        if (response.status() >= 300 && response.status() < 400) return route.abort();
        return await route.fulfill({response});
      } catch {
        return route.abort();
      }
    });
    await context.routeWebSocket('**/*', socket => socket.close());
    const page = await context.newPage();
    page.setDefaultTimeout(8000);
    let consoleErrors = 0;
    let failedRequests = 0;
    let apiErrors = 0;
    page.on('console', message => { if (message.type() === 'error') consoleErrors++; });
    page.on('pageerror', () => consoleErrors++);
    page.on('requestfailed', request => { if (request.method() === 'GET') failedRequests++; });
    page.on('response', response => {
      if (response.status() >= 400 && new URL(response.url()).pathname.startsWith('/api/v1/')) apiErrors++;
    });
    await page.goto(origin, {waitUntil: 'domcontentloaded'});
    await page.locator('#password').fill(password);
    password = undefined;
    const loginResponse = page.waitForResponse(response =>
      new URL(response.url()).pathname === '/api/v1/auth/login' && response.request().method() === 'POST');
    await page.locator('#auth-form button[type=submit]').click();
    if (!(await loginResponse).ok()) throw new Error('Audit sign-in failed');
    await page.locator('#logout').waitFor({state: 'visible'});
    const records = [];
    for (const [viewport, width] of [['desktop', 1440], ['mobile', 390]]) {
      await page.setViewportSize({width, height: 900});
      for (const tab of pages) {
        const chooser = page.locator('#home-panel:visible');
        if (tab !== 'home' && await chooser.count()) {
          const select = page.locator('#home-select-all');
          const proceed = page.locator('#home-continue');
          if (await select.isEnabled()) await select.click();
          if (await proceed.isEnabled()) await proceed.click();
          else await page.locator('#choose-global').click();
        }
        const nav = tab === 'home'
          ? await chooser.count() ? null : page.locator('#scope-bar button[data-tab="home"]')
          : page.locator(`#primary-navigation button[data-tab="${tab}"]:not([data-config-shortcut])`).first();
        if (nav && !await nav.isVisible() && await page.locator('#sidebar-toggle').isVisible()) {
          await page.locator('#sidebar-toggle').click();
        }
        if (nav && (!await nav.isVisible() || !await nav.isEnabled())) {
          records.push({viewport, tab, result: 'unavailable'});
          continue;
        }
        const before = [consoleErrors, failedRequests, apiErrors, blockedWrites];
        try {
          if (nav) await nav.click();
          await page.waitForLoadState('networkidle', {timeout: 5000});
          const panel = page.locator(`#${tab}-panel:visible`);
          let detailFailures = 0;
          for (const details of await panel.locator('details:not([open]) > summary:visible').all()) {
            try { await details.click({timeout: 1000}); } catch { detailFailures++; }
          }
          const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
          if (fixture) await page.screenshot({path: join(output, `${viewport}-${tab}.png`), animations: 'disabled'});
          records.push({viewport, tab, result: await panel.count() ? 'rendered' : 'panel-unverified', overflow, detailFailures,
            consoleErrors: consoleErrors - before[0], failedRequests: failedRequests - before[1],
            apiErrors: apiErrors - before[2], blockedWrites: blockedWrites - before[3]});
        } catch {
          records.push({viewport, tab, result: 'navigation-or-settle-failed'});
        }
      }
    }
    const report = ['# VotingPlugin-Control WebUI Audit', '',
      `Chromium ${browser.version()}. Authenticated browser session completed.`, '',
      '| Viewport | Page | Result | Overflow | Console | Network | API | Blocked writes | Detail failures |',
      '| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: |',
      ...records.map(row => `| ${[row.viewport, row.tab, row.result, row.overflow, row.consoleErrors,
        row.failedRequests, row.apiErrors, row.blockedWrites, row.detailFailures].map(value => value ?? '—').join(' | ')} |`),
      '', 'Unavailable values and blocked inspections require separate evidence.'].join('\n');
    await writeFile(join(output, 'report.md'), report, {mode: 0o600});
    console.log('Authenticated audit finished. See the private report for page results.');
  } finally {
    await browser.close();
  }
}

module.exports = {trustedOrigin, requestAllowed};
if (require.main === module) audit().catch(() => {
  console.error('Audit stopped before completion. Check browser dependencies, origin, and owner authentication.');
  process.exitCode = 1;
});
