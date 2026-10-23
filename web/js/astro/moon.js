// Moon phase, illumination and rise/set: a port of the Android app's MoonCalculator.kt (lunar position from Paul
// Schlyter's "How to compute planetary positions", about 2' accuracy). Instants are epoch seconds.
import { startOfDay, addDays } from '../tz.js';

const rad = (d) => (d * Math.PI) / 180;
const deg = (r) => (r * 180) / Math.PI;
const norm360 = (x) => x - 360 * Math.floor(x / 360);
const floorMod = (a, b) => ((a % b) + b) % b;

const SCAN_STEP_S = 600;
const QUARTER_STEP_S = 6 * 3600;
const EARTH_RADII_PER_AU = 23454.8;

export const QUARTERS = [
  { key: 'new', elongation: 0 }, { key: 'first', elongation: 90 }, { key: 'full', elongation: 180 }, { key: 'last', elongation: 270 },
];

/** Days since 2000 Jan 0.0 UT (Schlyter's epoch). */
const dayNumber = (t) => t / 86400 + 2440587.5 - 2451543.5;

function sunTrue(d) {
  const ms = norm360(356.0470 + 0.9856002585 * d);
  const ws = 282.9404 + 4.70935e-5 * d;
  const e = 0.016709 - 1.151e-9 * d;
  const ecc = ms + deg(e * Math.sin(rad(ms)) * (1 + e * Math.cos(rad(ms))));
  const xv = Math.cos(rad(ecc)) - e;
  const yv = Math.sqrt(1 - e * e) * Math.sin(rad(ecc));
  return { lon: norm360(deg(Math.atan2(yv, xv)) + ws), distanceAu: Math.hypot(xv, yv), meanLon: norm360(ms + ws) };
}

function moonEcliptic(d) {
  const n = norm360(125.1228 - 0.0529538083 * d);
  const i = 5.1454;
  const w = norm360(318.0634 + 0.1643573223 * d);
  const a = 60.2666;
  const e = 0.054900;
  const m = norm360(115.3654 + 13.0649929509 * d);

  let ecc = m + deg(e * Math.sin(rad(m)) * (1 + e * Math.cos(rad(m))));
  for (let k = 0; k < 3; k++) ecc -= (ecc - deg(e * Math.sin(rad(ecc))) - m) / (1 - e * Math.cos(rad(ecc)));
  const xv = a * (Math.cos(rad(ecc)) - e);
  const yv = a * Math.sqrt(1 - e * e) * Math.sin(rad(ecc));
  const v = deg(Math.atan2(yv, xv));
  let r = Math.hypot(xv, yv);

  const xh = r * (Math.cos(rad(n)) * Math.cos(rad(v + w)) - Math.sin(rad(n)) * Math.sin(rad(v + w)) * Math.cos(rad(i)));
  const yh = r * (Math.sin(rad(n)) * Math.cos(rad(v + w)) + Math.cos(rad(n)) * Math.sin(rad(v + w)) * Math.cos(rad(i)));
  const zh = r * Math.sin(rad(v + w)) * Math.sin(rad(i));
  let lon = deg(Math.atan2(yh, xh));
  let lat = deg(Math.atan2(zh, Math.hypot(xh, yh)));

  // Perturbations
  const sun = sunTrue(d);
  const ms = norm360(356.0470 + 0.9856002585 * d);
  const lm = norm360(n + w + m);
  const dm = norm360(lm - sun.meanLon);
  const f = norm360(lm - n);
  const s = (x) => Math.sin(rad(x));
  const c = (x) => Math.cos(rad(x));
  lon += -1.274 * s(m - 2 * dm) + 0.658 * s(2 * dm) - 0.186 * s(ms) - 0.059 * s(2 * m - 2 * dm) -
    0.057 * s(m - 2 * dm + ms) + 0.053 * s(m + 2 * dm) + 0.046 * s(2 * dm - ms) + 0.041 * s(m - ms) -
    0.035 * s(dm) - 0.031 * s(m + ms) - 0.015 * s(2 * f - 2 * dm) + 0.011 * s(m - 4 * dm);
  lat += -0.173 * s(f - 2 * dm) - 0.055 * s(m - f - 2 * dm) - 0.046 * s(m + f - 2 * dm) + 0.033 * s(f + 2 * dm) + 0.017 * s(2 * m + f);
  r += -0.58 * c(m - 2 * dm) - 0.46 * c(2 * dm);
  return { lon: norm360(lon), lat, distance: r };
}

/** Illuminated fraction (0..1) and elongation (Moon − Sun ecliptic longitude: 0 new, 90 first quarter, 180 full, 270 last). */
export function phase(t) {
  const d = dayNumber(t);
  const moon = moonEcliptic(d);
  const sun = sunTrue(d);
  const cosElong = Math.cos(rad(moon.lon - sun.lon)) * Math.cos(rad(moon.lat));
  const elong = Math.acos(Math.max(-1, Math.min(1, cosElong)));
  const sunDist = sun.distanceAu * EARTH_RADII_PER_AU;
  const phaseAngle = Math.atan2(sunDist * Math.sin(elong), moon.distance - sunDist * cosElong);
  const elongation = norm360(moon.lon - sun.lon);
  return { illumination: (1 + Math.cos(phaseAngle)) / 2, elongation, waxing: elongation < 180, name: phaseName(elongation) };
}

/** Principal phases get a ±10° window around their exact elongation. */
export function phaseName(e) {
  if (e < 10 || e >= 350) return 'new';
  if (e < 80) return 'waxingCrescent';
  if (e < 100) return 'firstQuarter';
  if (e < 170) return 'waxingGibbous';
  if (e < 190) return 'full';
  if (e < 260) return 'waningGibbous';
  if (e < 280) return 'lastQuarter';
  return 'waningCrescent';
}

/** (altitude°, horizontal parallax°), topocentric, no refraction. */
function topocentric(t, latitude, longitude) {
  const d = dayNumber(t);
  const moon = moonEcliptic(d);
  const ecl = rad(23.4393 - 3.563e-7 * d);
  const x = Math.cos(rad(moon.lon)) * Math.cos(rad(moon.lat));
  const y = Math.sin(rad(moon.lon)) * Math.cos(rad(moon.lat));
  const z = Math.sin(rad(moon.lat));
  const ye = y * Math.cos(ecl) - z * Math.sin(ecl);
  const ze = y * Math.sin(ecl) + z * Math.cos(ecl);
  const ra = deg(Math.atan2(ye, x));
  const dec = deg(Math.atan2(ze, Math.hypot(x, ye)));
  const gmst0 = sunTrue(d).meanLon + 180;
  const ut = floorMod(Math.floor(t), 86400) / 3600;
  const ha = rad(gmst0 + ut * 15 + longitude - ra);
  const alt = deg(Math.asin(Math.sin(rad(latitude)) * Math.sin(rad(dec)) + Math.cos(rad(latitude)) * Math.cos(rad(dec)) * Math.cos(ha)));
  const parallax = deg(Math.asin(1 / moon.distance));
  return [alt - parallax * Math.cos(rad(alt)), parallax];
}

/** Topocentric altitude of the Moon's centre in degrees, without refraction. */
export const altitude = (t, latitude, longitude) => topocentric(t, latitude, longitude)[0];

/** Altitude above the rise/set threshold: upper limb on the horizon with standard refraction and the semi-diameter. */
function horizonOffset(t, latitude, longitude) {
  const [alt, parallax] = topocentric(t, latitude, longitude);
  return alt - (-0.5667 - 0.2725 * parallax);
}

function bisect(t0, t1, latitude, longitude) {
  let a = t0;
  let b = t1;
  const fa = horizonOffset(a, latitude, longitude) < 0;
  while (b - a > 5) {
    const m = Math.floor((a + b) / 2);
    if ((horizonOffset(m, latitude, longitude) < 0) === fa) a = m; else b = m;
  }
  return Math.floor((a + b) / 2);
}

/** Rise and set within the local calendar day [date]; either may be null (or both, near the poles). */
export function riseSet(date, latitude, longitude, tz) {
  const start = startOfDay(date, tz);
  const end = startOfDay(addDays(date, 1), tz);
  let rise = null;
  let set = null;
  let t = start;
  let f = horizonOffset(t, latitude, longitude);
  while (t < end) {
    const t2 = Math.min(t + SCAN_STEP_S, end);
    const f2 = horizonOffset(t2, latitude, longitude);
    if ((f < 0) !== (f2 < 0)) {
      const crossing = bisect(t, t2, latitude, longitude);
      if (f < 0) { if (rise == null) rise = crossing; } else if (set == null) set = crossing;
    }
    t = t2;
    f = f2;
  }
  return { rise, set };
}

/** The next time after [from] the elongation reaches [quarter] (0, 90, 180 or 270), within a minute. */
export function nextQuarter(elongation, from) {
  const offset = (t) => norm360(phase(t).elongation - elongation + 180) - 180;
  let a = Math.floor(from);
  while (!(offset(a) < 0 && offset(a + QUARTER_STEP_S) >= 0)) a += QUARTER_STEP_S;
  let b = a + QUARTER_STEP_S;
  while (b - a > 30) {
    const m = Math.floor((a + b) / 2);
    if (offset(m) < 0) a = m; else b = m;
  }
  return b;
}

/** The next occurrence of each principal phase after [from], in chronological order: [{ key, time }]. */
export function nextQuarters(from) {
  return QUARTERS.map((q) => ({ key: q.key, time: nextQuarter(q.elongation, from) })).sort((x, y) => x.time - y.time);
}

/** Distance from the Earth's centre in km. */
export const distanceKm = (t) => moonEcliptic(dayNumber(t)).distance * 6378.14;
