// The Sun's curve over a day and the Moon's disc, as in the Android app's Sun and Moon page.
import * as sun from './astro/sun.js';
import { prepare } from './charts.js';
import { css, withAlpha, blend, skyColor } from './color.js';
import { SKY_STOPS } from './themes.js';
import { startOfDay, addDays, minutesOfDay } from './tz.js';

const HORIZON = sun.HORIZON_DEG;
const BANDS = [[HORIZON, -6, 0.27], [-6, -12, 0.18], [-12, -18, 0.10]]; // civil, nautical, astronomical

/** Colour of the curve at an elevation, by the curve style (sky ramp, accent, golden/blue hour, twilights). */
export function curveColor(p, style, el) {
  if (style === 'sky') return skyColor(p, SKY_STOPS, el);
  if (style === 'golden') {
    if (el >= 6) return p.highSun;
    if (el >= -4) return p.goldenHour;
    if (el >= -6) return p.blueHour;
    return p.dim;
  }
  return el > HORIZON ? p.accent : p.dim;
}

const dimmed = (style, el) => (style === 'sky' ? false : style === 'golden' ? el < -6 : el <= HORIZON);

/** Draws the Sun's elevation over the local day [date]; [now] (epoch seconds) puts a dot on the curve. */
export function drawSunCurve(canvas, { date, lat, lon, tz, palette: p, style, width, now }) {
  const height = 190;
  const g = prepare(canvas, width, height);
  const start = startOfDay(date, tz);
  const end = startOfDay(addDays(date, 1), tz);
  const steps = 288;
  const els = [];
  for (let i = 0; i <= steps; i++) els.push(sun.elevation(start + ((end - start) * i) / steps, lat, lon));
  const top = Math.max(20, Math.ceil(Math.max(...els) / 10) * 10 + 5);
  const bottom = -20;
  const padL = 30; const padR = 10; const padT = 10; const padB = 24;
  const x = (i) => padL + (i / steps) * (width - padL - padR);
  const y = (e) => padT + ((top - e) / (top - bottom)) * (height - padT - padB);

  if (style === 'twilight') {
    for (const [from, to, alpha] of BANDS) {
      g.fillStyle = withAlpha(p.twilightBand, alpha);
      g.fillRect(padL, y(from), width - padL - padR, y(to) - y(from));
    }
  }
  g.font = '10px system-ui, sans-serif';
  g.textAlign = 'right';
  for (let e = -18; e <= top; e += e < 0 ? 6 : 30) {
    if (e === 0) continue; // the horizon line below stands for it
    g.strokeStyle = css(blend(p.card, p.text, e === 0 ? 0.3 : 0.1)); g.lineWidth = 1;
    g.beginPath(); g.moveTo(padL, y(e)); g.lineTo(width - padR, y(e)); g.stroke();
    g.fillStyle = css(p.textSecondary); g.fillText(`${e}°`, padL - 4, y(e) + 3);
  }
  g.strokeStyle = css(p.dim); g.lineWidth = 1.2;
  g.beginPath(); g.moveTo(padL, y(HORIZON)); g.lineTo(width - padR, y(HORIZON)); g.stroke();

  g.save();
  g.beginPath(); g.rect(padL, padT - 4, width - padL - padR, height - padT - padB + 4); g.clip(); // what is under -20° stays out
  g.lineWidth = 3; g.lineCap = 'round';
  for (let i = 0; i < steps; i++) {
    const mid = (els[i] + els[i + 1]) / 2;
    g.globalAlpha = dimmed(style, mid) ? 0.47 : 1;
    g.strokeStyle = css(curveColor(p, style, mid));
    g.beginPath(); g.moveTo(x(i), y(els[i])); g.lineTo(x(i + 1), y(els[i + 1])); g.stroke();
  }
  g.globalAlpha = 1;
  g.restore();

  // Hours under the curve.
  g.textAlign = 'center'; g.fillStyle = css(p.textSecondary);
  for (let h = 0; h <= 24; h += 6) {
    const i = (h / 24) * steps;
    g.fillText(String(h % 24).padStart(2, '0'), padL + (i / steps) * (width - padL - padR), height - 8);
  }
  if (now != null && now >= start && now < end) {
    const i = ((now - start) / (end - start)) * steps;
    const e = sun.elevation(now, lat, lon);
    g.fillStyle = css(curveColor(p, style, e)); g.strokeStyle = css(p.card); g.lineWidth = 2;
    g.beginPath(); g.arc(padL + (i / steps) * (width - padL - padR), y(e), 6, 0, 2 * Math.PI); g.fill(); g.stroke();
  }
}

/** The Moon's disc: lit part and terminator from the illuminated fraction; mirrored in the southern hemisphere. */
export function drawMoon(canvas, { illumination, waxing, southern, palette: p, size }) {
  const g = prepare(canvas, size, size);
  const r = size / 2 - 1;
  const c = size / 2;
  const dark = css(blend(p.card, p.text, p.light ? 0.45 : 0.08));
  const lit = '#ECE6D2';
  g.fillStyle = dark;
  g.beginPath(); g.arc(c, c, r, 0, 2 * Math.PI); g.fill();
  g.save();
  if (waxing === southern) { g.translate(size, 0); g.scale(-1, 1); } // northern hemisphere: waxing is lit on the right
  const k = illumination;
  const rx = Math.abs(1 - 2 * k) * r;
  g.beginPath();
  g.moveTo(c, c - r);
  g.arc(c, c, r, -Math.PI / 2, Math.PI / 2, false); // the lit limb: right half, top to bottom
  // the terminator: half-ellipse back to the top, bulging right for a crescent and left for a gibbous Moon
  g.ellipse(c, c, rx, r, 0, Math.PI / 2, -Math.PI / 2, k >= 0.5 ? false : true);
  g.closePath();
  g.fillStyle = lit;
  g.fill();
  g.restore();
  g.strokeStyle = withAlpha(lit, 0.27); g.lineWidth = Math.max(1, size / 48);
  g.beginPath(); g.arc(c, c, r - 0.5, 0, 2 * Math.PI); g.stroke();
}

export { minutesOfDay };
