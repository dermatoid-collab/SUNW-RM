import test from 'node:test';
import assert from 'node:assert/strict';
import * as sun from '../js/astro/sun.js';
import * as moon from '../js/astro/moon.js';
import { hhmm, minutesOfDay, at, dateOf, startOfDay } from '../js/tz.js';

const near = (expected, epoch, tz, tolMin = 1) => {
  const [h, m] = expected.split(':').map(Number);
  let diff = minutesOfDay(epoch, tz) - (h * 60 + m);
  if (diff > 720) diff -= 1440;
  if (diff < -720) diff += 1440;
  assert.ok(Math.abs(diff) <= tolMin, `expected ~${expected}, got ${hhmm(epoch, tz)}`);
};

// Reference values from the NOAA Solar Calculator, the same the Android tests use.
test('Greenwich, summer solstice', () => {
  const tz = 'Europe/London';
  const d = sun.day('2024-06-21', 51.4769, 0.0, tz);
  assert.equal(d.kind, 'normal');
  near('04:43', d.sunrise, tz);
  near('21:21', d.sunset, tz);
  near('13:02', d.solarNoon, tz);
});

test('New York, winter solstice', () => {
  const tz = 'America/New_York';
  const d = sun.day('2024-12-21', 40.7128, -74.006, tz);
  near('07:17', d.sunrise, tz);
  near('16:32', d.sunset, tz);
});

test('southern hemisphere has long days in January', () => {
  const tz = 'Australia/Sydney';
  const jan = sun.day('2026-01-15', -33.8688, 151.2093, tz);
  const jul = sun.day('2026-07-15', -33.8688, 151.2093, tz);
  assert.ok(jan.length > jul.length);
  assert.ok(jan.length / 3600 >= 14);
});

test('polar day and night', () => {
  const tz = 'Europe/Oslo';
  assert.equal(sun.day('2026-06-21', 69.6496, 18.956, tz).kind, 'polarDay');
  assert.equal(sun.day('2026-12-21', 69.6496, 18.956, tz).kind, 'polarNight');
});

test('elevation at equinox noon is 90° minus latitude; azimuth east, south, west', () => {
  const tz = 'Europe/Rome';
  const lat = 44.8015, lon = 10.3279;
  const d = sun.day('2026-03-20', lat, lon, tz);
  assert.ok(Math.abs(sun.elevation(d.solarNoon, lat, lon) - 45.2) < 0.5);
  assert.ok(Math.abs(sun.azimuth(d.sunrise, lat, lon) - 90) < 2);
  assert.ok(Math.abs(sun.azimuth(d.solarNoon, lat, lon) - 180) < 0.5);
  assert.ok(Math.abs(sun.azimuth(d.sunset, lat, lon) - 270) < 2);
  const june = sun.day('2026-06-21', lat, lon, tz);
  assert.ok(Math.abs(sun.azimuth(june.sunrise, lat, lon) - 55) < 2);
});

test('twilight crossings come before sunrise', () => {
  const tz = 'Europe/Rome';
  const civil = sun.crossing('2026-10-07', 44.8015, 10.3279, tz, -6);
  const d = sun.day('2026-10-07', 44.8015, 10.3279, tz);
  assert.ok(civil.morning < d.sunrise && civil.evening > d.sunset);
});

// Reference values from PyEphem, the same the Android tests use.
test('moon illumination', () => {
  const cases = {
    '2026-10-07T18:33:00Z': 9.39, '2026-10-18T16:00:00Z': 50.04, '2026-10-26T04:00:00Z': 99.84,
    '2027-03-14T20:00:00Z': 40.57, '2025-12-25T09:00:00Z': 25.39,
  };
  for (const [t, expected] of Object.entries(cases)) {
    const got = moon.phase(Date.parse(t) / 1000).illumination * 100;
    assert.ok(Math.abs(got - expected) <= 0.3, `${t}: ${got} vs ${expected}`);
  }
});

test('moon phase names', () => {
  const p = moon.phase(Date.parse('2026-10-07T18:33:00Z') / 1000);
  assert.equal(p.name, 'waningCrescent');
  assert.equal(p.waxing, false);
  assert.equal(moon.phase(Date.parse('2026-10-18T16:00:00Z') / 1000).name, 'firstQuarter');
  assert.equal(moon.phase(Date.parse('2026-10-26T04:00:00Z') / 1000).name, 'full');
});

test('moon rise and set', () => {
  const rome = moon.riseSet('2026-10-07', 44.8015, 10.3279, 'Europe/Rome');
  near('03:34', rome.rise, 'Europe/Rome', 2);
  near('17:20', rome.set, 'Europe/Rome', 2);
  const ny = moon.riseSet('2027-02-03', 40.7128, -74.006, 'America/New_York');
  near('05:26', ny.rise, 'America/New_York', 2);
  near('14:22', ny.set, 'America/New_York', 2);
  const sydney = moon.riseSet('2026-10-20', -33.8688, 151.2093, 'Australia/Sydney');
  near('13:10', sydney.rise, 'Australia/Sydney', 2);
  near('02:39', sydney.set, 'Australia/Sydney', 2);
});

test('next quarters', () => {
  const from = Date.parse('2026-10-07T00:00:00Z') / 1000;
  const expected = [['new', '2026-10-10T15:50:00Z'], ['first', '2026-10-18T16:12:00Z'], ['full', '2026-10-26T04:11:00Z'], ['last', '2026-11-01T20:28:00Z']];
  const actual = moon.nextQuarters(from);
  assert.deepEqual(actual.map((q) => q.key), expected.map((q) => q[0]));
  for (const [i, [, iso]] of expected.entries()) {
    assert.ok(Math.abs(actual[i].time - Date.parse(iso) / 1000) <= 15 * 60, `${expected[i][0]}: ${new Date(actual[i].time * 1000).toISOString()}`);
  }
});

test('time zones: local midnight across a change of clock', () => {
  // 2026-10-25 has 25 hours in Rome (clocks go back).
  const a = startOfDay('2026-10-25', 'Europe/Rome');
  const b = startOfDay('2026-10-26', 'Europe/Rome');
  assert.equal((b - a) / 3600, 25);
  assert.equal(dateOf(at('2026-10-25', 23, 30, 'Europe/Rome'), 'Europe/Rome'), '2026-10-25');
});
