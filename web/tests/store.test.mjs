import test from 'node:test';
import assert from 'node:assert/strict';
import { Store } from '../js/store.js';
import { exportBackup, importBackup } from '../js/backup.js';
import { parseForecast, last24Hours, last24Temperature, parseSearch, matches, describeCode } from '../js/weather.js';
import { THEMES } from '../js/themes.js';

const fakeStorage = () => { const m = new Map(); return { getItem: (k) => m.get(k) ?? null, setItem: (k, v) => m.set(k, v) }; };

test('recents keep ten, newest first, without repeats', () => {
  const s = new Store(fakeStorage());
  for (let i = 1; i <= 12; i++) s.addRecent({ name: 'Place ' + i, lat: 40 + i, lon: 10 });
  s.addRecent({ name: 'Place 5', lat: 45, lon: 10 });
  const names = s.recents.map((r) => r.name);
  assert.equal(names.length, 10);
  assert.deepEqual(names.slice(0, 3), ['Place 5', 'Place 12', 'Place 11']);
  assert.equal(names.filter((n) => n === 'Place 5').length, 1);
});

test('the backup goes round: export, empty browser, import', () => {
  const a = new Store(fakeStorage());
  a.place = { lat: 46.5287, lon: 10.4527, name: 'Passo dello Stelvio', automatic: false };
  a.addFavorite({ name: 'Livigno', lat: 46.5386, lon: 10.1357 });
  a.addFavorite({ name: '🏠', lat: 44.73, lon: 10.37 });
  a.curve = 'twilight'; a.accent = 'tokyo_blue'; a.theme = 'sage_dark'; a.stepHours = 3;
  a.addRecent({ name: 'Livigno', lat: 46.5386, lon: 10.1357 });
  a.addRecent({ name: 'Bormio', lat: 46.4669, lon: 10.3705 });
  const json = exportBackup(a);

  const b = new Store(fakeStorage());
  importBackup(b, json);
  assert.equal(b.place.name, 'Passo dello Stelvio');
  assert.deepEqual(b.favorites.map((f) => f.name), ['Livigno', '🏠']);
  assert.equal(b.curve, 'twilight'); assert.equal(b.accent, 'tokyo_blue'); assert.equal(b.theme, 'sage_dark');
  assert.equal(b.stepHours, 3);
  assert.deepEqual(b.recents.map((r) => r.name), ['Bormio', 'Livigno']);
});

test('an Android backup imports, and what the web does not use is given back on export', () => {
  const android = {
    app: 'it.sunw.widget', version: 2,
    place: { automatic: true, lat: 44.8, lon: 10.3, name: null }, favorites: [],
    appearance: { curve: 'sky', accent: 'amber', theme: 'anthracite' },
    alerts: { notify: false },
    watch: { enabled: false, events: { hail: false }, thresholds: { heat: { on: 36 } }, general: { dedup: 8 } },
    digest: ['06:30', '18:00'], weatherDay: { stepHours: 3 }, recents: [],
    webcams: [{ name: 'Passo', url: 'https://example.org/cam' }],
  };
  const s = new Store(fakeStorage());
  importBackup(s, JSON.stringify(android));
  const out = JSON.parse(exportBackup(s));
  assert.deepEqual(out.watch, android.watch);
  assert.deepEqual(out.digest, android.digest);
  assert.deepEqual(out.alerts, android.alerts);
  assert.deepEqual(out.webcams, android.webcams);
  assert.equal(out.version, 2);
  // Version 1 files (no newer sections) import too, and other files are refused.
  importBackup(s, JSON.stringify({ app: 'it.sunw.widget', version: 1, favorites: [] }));
  assert.throws(() => importBackup(s, '{"hello":"world"}'));
});

test('every theme has what the page needs, and Legacy is first and alone', () => {
  assert.equal(THEMES[0].key, 'anthracite');
  assert.equal(THEMES.filter((t) => t.group === 'LEGACY').length, 1);
  assert.equal(THEMES.length, 39);
  for (const t of THEMES) {
    assert.equal(t.sky.length, 8, t.key);
    for (const k of ['background', 'text', 'textSecondary', 'dim', 'page', 'card', 'blueHour', 'goldenHour', 'highSun', 'twilightBand']) assert.ok(t[k], t.key + '.' + k);
  }
  assert.equal(THEMES.filter((t) => t.light).length, 13);
  assert.equal(THEMES.filter((t) => t.group === 'MINIMALIST').length, 8);
});

const sample = () => ({
  timezone: 'Europe/Rome',
  hourly: {
    time: ['2026-10-22T10:00', '2026-10-22T11:00', '2026-10-22T12:00'], temperature_2m: [12, 14, null],
    apparent_temperature: [10, 12, 13], precipitation: [0, 0.2, 0], precipitation_probability: [10, 40, null], weather_code: [1, 61, 3],
    wind_speed_10m: [5, 8, 9], wind_direction_10m: [270, 280, 290], wind_gusts_10m: [12, 20, 22], pressure_msl: [1012.5, 1012, 1011.5], is_day: [1, 1, 1],
  },
  minutely_15: { time: ['2026-10-22T11:00', '2026-10-22T11:15'], temperature_2m: [14, 14.2] },
  daily: { time: ['2026-10-22'], weather_code: [61], temperature_2m_max: [15], temperature_2m_min: [8], precipitation_sum: [1.2], precipitation_probability_max: [60], wind_gusts_10m_max: [30], sunrise: ['2026-10-22T07:30'], sunset: ['2026-10-22T18:10'] },
});

test('Open-Meteo parsing', () => {
  const f = parseForecast(sample());
  assert.equal(f.tz, 'Europe/Rome');
  assert.equal(f.hours.length, 3);
  assert.equal(f.hours[1].gust, 20);
  assert.equal(f.hours[2].temp, null); assert.equal(f.hours[2].prob, null);
  assert.equal(f.fine.length, 2);
  assert.equal(f.days[0].tmax, 15);
  assert.ok(f.days[0].sunset > f.days[0].sunrise);
  assert.throws(() => parseForecast({ error: true, reason: 'nope' }));
  assert.throws(() => parseForecast({}));
});

test('the last 24 hours: 15-minute temperatures when there are, hourly otherwise', () => {
  const f = parseForecast(sample());
  const now = f.fine[1].t + 60;
  assert.equal(last24Temperature(f, now).length, 2);
  assert.equal(last24Temperature({ ...f, fine: [] }, f.hours[2].t + 1).length, 2); // the hour without a temperature is left out
  assert.equal(last24Hours(f, f.hours[2].t + 5).length, 3);
});

test('place search: results and what counts as a match', () => {
  const r = parseSearch({ results: [{ name: 'Prato Spilla', latitude: 44.4, longitude: 10.0, admin2: 'Parma', admin1: 'Emilia-Romagna', country: 'Italia' }] });
  assert.equal(r[0].name, 'Prato Spilla');
  assert.equal(r[0].detail, 'Prato Spilla, Parma, Emilia-Romagna, Italia');
  assert.ok(matches('prato spilla', r[0]));
  assert.ok(matches('Forlì', { name: 'Forli' }));
  assert.ok(!matches('Prato Spilla', { name: 'Monchio delle Corti' }));
  assert.equal(describeCode(61).symbol, '🌧️');
});

import { makeForecast } from './fixtures.mjs';
test('the fixture parses like the real thing', () => {
  const f = parseForecast(makeForecast(Date.parse('2026-10-23T10:20:00Z')));
  assert.equal(f.hours.length, 9 * 24);
  assert.equal(f.days.length, 9);
  assert.ok(f.fine.length > 90);
  assert.ok(last24Hours(f, Date.parse('2026-10-23T10:20:00Z') / 1000).length === 25);
});
