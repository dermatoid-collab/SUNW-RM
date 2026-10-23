// Solar position and sunrise/sunset from the NOAA Solar Calculator equations (Meeus, "Astronomical Algorithms"):
// a port of the Android app's SunCalculator.kt. Instants are epoch seconds. Accuracy: well under a minute
// for latitudes within ±72°.
import { noon as localNoon } from '../tz.js';

export const HORIZON_DEG = -0.833;

const rad = (d) => (d * Math.PI) / 180;
const deg = (r) => (r * 180) / Math.PI;
const norm360 = (x) => x - 360 * Math.floor(x / 360);
const wrap180 = (x) => norm360(x + 180) - 180;
const floorMod = (a, b) => ((a % b) + b) % b;

function solarState(t) {
  const jd = t / 86400 + 2440587.5;
  const c = (jd - 2451545.0) / 36525.0; // Julian centuries since J2000.0
  const l0 = norm360(280.46646 + c * (36000.76983 + c * 0.0003032));
  const m = 357.52911 + c * (35999.05029 - 0.0001537 * c);
  const e = 0.016708634 - c * (0.000042037 + 0.0000001267 * c);
  const mRad = rad(m);
  const center = Math.sin(mRad) * (1.914602 - c * (0.004817 + 0.000014 * c)) +
    Math.sin(2 * mRad) * (0.019993 - 0.000101 * c) + Math.sin(3 * mRad) * 0.000289;
  const omega = rad(125.04 - 1934.136 * c);
  const apparentLong = rad(l0 + center - 0.00569 - 0.00478 * Math.sin(omega));
  const meanObliquity = 23 + (26 + (21.448 - c * (46.815 + c * (0.00059 - c * 0.001813))) / 60) / 60;
  const obliquity = rad(meanObliquity + 0.00256 * Math.cos(omega));
  const declination = Math.asin(Math.sin(obliquity) * Math.sin(apparentLong));
  const y = Math.tan(obliquity / 2) ** 2;
  const l0Rad = rad(l0);
  const eqTime = 4 * deg(
    y * Math.sin(2 * l0Rad) - 2 * e * Math.sin(mRad) + 4 * e * y * Math.sin(mRad) * Math.cos(2 * l0Rad) -
    0.5 * y * y * Math.sin(4 * l0Rad) - 1.25 * e * e * Math.sin(2 * mRad));
  return { declinationRad: declination, equationOfTimeMin: eqTime };
}

/** Local hour angle in degrees, wrapped to [-180, 180). */
function hourAngleDeg(t, longitude, s) {
  const utcMinutes = floorMod(Math.floor(t), 86400) / 60;
  return wrap180((utcMinutes + s.equationOfTimeMin + 4 * longitude) / 4 - 180);
}

function cosHourAngle(latRad, declRad, altitudeDeg) {
  return (Math.sin(rad(altitudeDeg)) - Math.sin(latRad) * Math.sin(declRad)) / (Math.cos(latRad) * Math.cos(declRad));
}

function solarNoon(near, longitude) {
  let t = near;
  for (let i = 0; i < 3; i++) {
    const ha = hourAngleDeg(t, longitude, solarState(t));
    t += Math.trunc(-ha * 240); // 1° of hour angle = 4 min = 240 s
  }
  return t;
}

function refineEvent(noon, latRad, longitude, rising, altitudeDeg) {
  let t = noon + (rising ? -6 * 3600 : 6 * 3600);
  for (let i = 0; i < 5; i++) {
    const s = solarState(t);
    const cosH0 = cosHourAngle(latRad, s.declinationRad, altitudeDeg);
    if (!(cosH0 >= -1 && cosH0 <= 1)) return null;
    const h0 = deg(Math.acos(cosH0));
    const delta = (rising ? -h0 : h0) - hourAngleDeg(t, longitude, s);
    t += Math.trunc(delta * 240);
    if (Math.abs(delta) < 0.001) return t;
  }
  return t;
}

/**
 * Sunrise, sunset and solar noon of [date] ('YYYY-MM-DD') at the place, in [tz].
 * `kind` is 'normal', 'polarNight' (the Sun never rises) or 'polarDay' (it never sets).
 */
export function day(date, latitude, longitude, tz) {
  const noon = solarNoon(localNoon(date, tz), longitude);
  const latRad = rad(latitude);
  const cosH0 = cosHourAngle(latRad, solarState(noon).declinationRad, HORIZON_DEG);
  if (cosH0 > 1) return { kind: 'polarNight', date, solarNoon: noon };
  if (cosH0 < -1) return { kind: 'polarDay', date, solarNoon: noon };
  const sunrise = refineEvent(noon, latRad, longitude, true, HORIZON_DEG);
  const sunset = refineEvent(noon, latRad, longitude, false, HORIZON_DEG);
  if (sunrise == null || sunset == null) return { kind: cosH0 > 0 ? 'polarNight' : 'polarDay', date, solarNoon: noon };
  return { kind: 'normal', date, solarNoon: noon, sunrise, sunset, length: sunset - sunrise };
}

/**
 * When the Sun's centre passes [altitudeDeg] on [date]: -6/-12/-18 for the civil/nautical/astronomical twilights,
 * -4 and +6 for the blue and golden hours. `morning` and `evening` are null when it doesn't that day.
 */
export function crossing(date, latitude, longitude, tz, altitudeDeg) {
  const noon = solarNoon(localNoon(date, tz), longitude);
  const latRad = rad(latitude);
  const c = cosHourAngle(latRad, solarState(noon).declinationRad, altitudeDeg);
  if (!(c >= -1 && c <= 1)) return { morning: null, evening: null };
  return {
    morning: refineEvent(noon, latRad, longitude, true, altitudeDeg),
    evening: refineEvent(noon, latRad, longitude, false, altitudeDeg),
  };
}

/** Solar azimuth in degrees clockwise from north (90 = east). */
export function azimuth(t, latitude, longitude) {
  const s = solarState(t);
  const latRad = rad(latitude);
  const ha = rad(hourAngleDeg(t, longitude, s));
  const az = deg(Math.atan2(Math.sin(ha), Math.cos(ha) * Math.sin(latRad) - Math.tan(s.declinationRad) * Math.cos(latRad)));
  return norm360(az + 180);
}

/** Geometric solar elevation (degrees, no refraction). */
export function elevation(t, latitude, longitude) {
  const s = solarState(t);
  const latRad = rad(latitude);
  const ha = rad(hourAngleDeg(t, longitude, s));
  const sinEl = Math.sin(latRad) * Math.sin(s.declinationRad) + Math.cos(latRad) * Math.cos(s.declinationRad) * Math.cos(ha);
  return deg(Math.asin(Math.max(-1, Math.min(1, sinEl))));
}
