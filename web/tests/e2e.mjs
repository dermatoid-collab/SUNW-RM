// Opens the page in a real (headless) browser with Open-Meteo and Windy answered by fixtures, and checks it works:
//   NODE_PATH=$(npm root -g) node tests/e2e.mjs [screenshots-dir]
import { createRequire } from 'node:module';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { makeForecast, geocoding } from './fixtures.mjs';

const { chromium } = createRequire(import.meta.url)('playwright'); // found through NODE_PATH

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const shots = process.argv[2];
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.webmanifest': 'application/manifest+json', '.png': 'image/png' };
const server = http.createServer((req, res) => {
  const file = path.join(root, decodeURIComponent(new URL(req.url, 'http://x').pathname).replace(/\/$/, '/index.html'));
  if (!file.startsWith(root) || !fs.existsSync(file)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': types[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => server.listen(0, r));
const base = `http://localhost:${server.address().port}/`;

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 412, height: 915 }, deviceScaleFactor: 2, locale: 'it-IT', timezoneId: 'Europe/Rome' });
const page = await context.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(m.text()); });
await page.route('https://api.open-meteo.com/**', (r) => r.fulfill({ json: makeForecast() }));
await page.route('https://geocoding-api.open-meteo.com/**', (r) => r.fulfill({ json: geocoding(new URL(r.request().url()).searchParams.get('name') || '') }));
await page.route('https://embed.windy.com/**', (r) => r.fulfill({ contentType: 'text/html', body: '<body style="background:#345;color:#fff;font:14px sans-serif;display:grid;place-items:center;height:100vh">Windy (simulato)</body>' }));

const check = (cond, message) => { if (!cond) { errors.push('FAILED: ' + message); } };
const shot = async (name, opts = {}) => { if (shots) { fs.mkdirSync(shots, { recursive: true }); await page.screenshot({ path: path.join(shots, name + '.png'), ...opts }); } };

await page.goto(base);
await page.waitForSelector('#c3:not([hidden]) canvas');
await page.waitForTimeout(300);
check((await page.textContent('#place')).startsWith('Parma'), 'the place is Parma');
check((await page.textContent('#sunTimes')).includes('Alba'), 'sunrise shown');
check((await page.$$eval('#hourlyTable tr', (r) => r.length)) > 20, 'hourly rows');
check((await page.textContent('#c3Readout')).includes('raffica'), 'wind readout has the gust');
check((await page.textContent('#c2Readout')).includes('pioggia'), 'pressure readout has the rain');
check((await page.textContent('#mgReadout')).includes('mbar'), 'meteogram readout has the pressure');
await shot('1-top', { fullPage: false });
await shot('2-full', { fullPage: true });

// The cursor: a pointer on a chart changes the readout.
const before = await page.textContent('#c1Readout');
await page.locator('#c1Canvas').scrollIntoViewIfNeeded();
const box = await (await page.$('#c1Canvas')).boundingBox();
await page.mouse.move(box.x + box.width * 0.3, box.y + 60);
await page.waitForTimeout(100);
check((await page.textContent('#c1Readout')) !== before, 'the cursor changes the temperature readout');
if (shots) await (await page.$('#c1')).screenshot({ path: path.join(shots, '3-cursor.png') });

// Place menu, search, recents, picked dialog.
await page.evaluate(() => window.scrollTo(0, 0));
await page.click('#place');
check((await page.$$eval('#placeMenu button', (b) => b.map((x) => x.textContent)))[0] === 'Cerca', 'Search is the first item');
await shot('4-menu', { clip: { x: 0, y: 0, width: 412, height: 320 } });
await page.click('#placeMenu button[data-action=search]');
await page.fill('#searchInput', 'Prato Spilla');
await page.click('#searchGo');
await page.waitForSelector('#pickedDialog[open]');
check((await page.textContent('#pickedTitle')) === 'Prato Spilla', 'the found place keeps its own name');
await shot('5-picked');
await page.click('#pickedAdd');
await page.waitForTimeout(200);
check((await page.textContent('#place')).startsWith('Prato Spilla'), 'the place changed');
await page.click('#place'); await page.click('#placeMenu button[data-action=search]');
check((await page.textContent('#searchBody')).includes('Prato Spilla'), 'Recents list the place');
await shot('6-recents');
await page.fill('#searchInput', 'Livigno'); await page.click('#searchGo');
await page.waitForFunction(() => document.getElementById('searchBody').textContent.includes('Scegli la località'));
check((await page.$$eval('#searchBody li', (l) => l.length)) === 2, 'two results to choose from');
await shot('7-results');
await page.keyboard.press('Escape');

// Settings: a theme.
await page.click('#settingsBtn');
await shot('8-settings');
await page.click('#settingsBody button[data-theme=sage_light]');
await page.waitForTimeout(200);
check((await page.evaluate(() => document.documentElement.dataset.light)) === '1', 'a light theme sets the light flag');
await page.evaluate(() => document.getElementById('settingsDialog').close());
await shot('9-light-full', { fullPage: true });
const json = await page.evaluate(async () => (await import('./js/backup.js')).exportBackup((await import('./js/store.js')).Store && new (await import('./js/store.js')).Store()));
check(JSON.parse(json).appearance.theme === 'sage_light', 'the backup has the theme');
check(JSON.parse(json).favorites.some((f) => f.name === 'Prato Spilla'), 'the backup has the favourite');

await browser.close();
server.close();
if (errors.length) { console.error(errors.join('\n')); process.exit(1); }
console.log('e2e ok');
