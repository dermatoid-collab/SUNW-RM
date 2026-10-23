// The canvases: the 24-hour charts (temperature; pressure with the rain bars; wind with gusts and direction arrows)
// and the meteogram. Their values are read out in DOM fields under the chart (see app.js), so the charts only draw the
// plot and tell which point is under a pointer. Ported from the Android app's Last24hView and MeteogramView.
import { blend, css, withAlpha, temperatureColor, PRESSURE_LINE, WIND_LINE, RAIN_COLOR, MAX_PILL, MIN_PILL, windColor } from './color.js';
import { parts, minutesOfDay } from './tz.js';
import { lang } from './i18n.js';

/** Sizes a canvas for the screen's pixel density and returns its 2D context in CSS pixels. */
export function prepare(canvas, width, height) {
  const ratio = (typeof window !== 'undefined' && window.devicePixelRatio) || 1;
  canvas.width = Math.round(width * ratio);
  canvas.height = Math.round(height * ratio);
  canvas.style.width = width + 'px';
  canvas.style.height = height + 'px';
  const g = canvas.getContext('2d');
  g.setTransform(ratio, 0, 0, ratio, 0, 0);
  return g;
}

const dash = (g, on, off) => g.setLineDash([on, off]);
const nightShade = (p) => blend(p.card, '#000000', p.light ? 0.12 : 0.35); // a lighter slab on a light theme

// ------------------------------------------------------------------------------------------------ 24 hours

const LEFT = 6;
const TOP = 14;
const PLOT = 138;
const ARROWS = 22;

export const KIND = { TEMPERATURE: 'temperature', PRESSURE: 'pressure', WIND: 'wind' };

/**
 * One chart of the last 24 hours. `points` are { t, v } (epoch seconds, value); for the wind `dir` (degrees it comes
 * from) and `gust` per point; for the pressure `rain` (mm of the hour that ends at each point).
 */
export class Last24Chart {
  constructor(canvas, kind) { this.canvas = canvas; this.kind = kind; this.points = []; this.cursor = -1; }

  get height() { return TOP + PLOT + 24 + (this.kind === KIND.WIND ? ARROWS : 0); }

  show({ points, dir = [], gust = [], rain = [], tz, isDay, palette, width }) {
    Object.assign(this, { points, dir, gust, rain, tz, isDay, p: palette, width });
    this.cursor = -1;
    this.draw();
  }

  get bottom() { return TOP + PLOT; }

  x(i) {
    const pts = this.points;
    if (pts.length < 2) return LEFT;
    const span = pts[pts.length - 1].t - pts[0].t;
    return LEFT + ((pts[i].t - pts[0].t) / span) * (this.width - 2 * LEFT);
  }

  indexAt(px) {
    let best = -1;
    let bestD = Infinity;
    for (let i = 0; i < this.points.length; i++) {
      const d = Math.abs(this.x(i) - px);
      if (d < bestD) { best = i; bestD = d; }
    }
    return best;
  }

  scale() {
    const values = this.points.map((q) => q.v).concat(this.kind === KIND.WIND ? this.gust.filter((x) => x != null) : []);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const lo = this.kind === KIND.WIND ? 0 : Math.floor(min - 1);
    const hi = Math.max(Math.ceil(max + 1), lo + 4);
    return { lo, hi };
  }

  y(v) {
    const { lo, hi } = this.scale();
    return this.bottom - ((v - lo) / (hi - lo)) * (this.bottom - TOP);
  }

  lineColor(i) {
    if (this.kind === KIND.PRESSURE) return PRESSURE_LINE;
    if (this.kind === KIND.WIND) return WIND_LINE;
    return temperatureColor((this.points[i].v + this.points[i + 1].v) / 2);
  }

  format(v) { return this.kind === KIND.WIND ? v.toFixed(0) : v.toFixed(1) + (this.kind === KIND.TEMPERATURE ? '°' : ''); }

  draw() {
    const pts = this.points;
    if (pts.length < 2) return;
    const g = prepare(this.canvas, this.width, this.height);
    const p = this.p;
    const n = pts.length;
    const bottom = this.bottom;

    // Night behind the curve.
    g.fillStyle = css(nightShade(p));
    for (let i = 0; i < n - 1; i++) if (!this.isDay(pts[i].t)) g.fillRect(this.x(i), TOP - 8, this.x(i + 1) - this.x(i), bottom - TOP + 8);

    if (this.kind === KIND.PRESSURE) this.drawRain(g);

    // Grid and labels.
    const { lo, hi } = this.scale();
    const step = Math.max(1, Math.round((hi - lo) / 4));
    g.font = '10px system-ui, sans-serif';
    g.textAlign = 'left';
    for (let v = lo; v <= hi; v += step) {
      const y = this.y(v);
      g.strokeStyle = css(blend(p.card, p.text, 0.12));
      g.lineWidth = 1;
      g.beginPath(); g.moveTo(0, y); g.lineTo(this.width, y); g.stroke();
      g.fillStyle = css(p.textSecondary);
      g.fillText(this.kind === KIND.TEMPERATURE ? `${Math.round(v)}°` : `${Math.round(v)}`, 2, y - 2);
    }

    if (this.kind === KIND.WIND && this.gust.some((x) => x != null)) this.drawGusts(g);

    // Curve with a soft fill.
    g.beginPath();
    g.moveTo(this.x(0), bottom);
    for (let i = 0; i < n; i++) g.lineTo(this.x(i), this.y(pts[i].v));
    g.lineTo(this.x(n - 1), bottom);
    g.closePath();
    g.fillStyle = withAlpha(p.text, 0.07);
    g.fill();
    g.lineWidth = 2.6;
    g.lineCap = 'round';
    for (let i = 0; i < n - 1; i++) {
      g.strokeStyle = css(this.lineColor(i));
      g.beginPath(); g.moveTo(this.x(i), this.y(pts[i].v)); g.lineTo(this.x(i + 1), this.y(pts[i + 1].v)); g.stroke();
    }

    // The lowest and highest points.
    const iMin = pts.reduce((b, q, i) => (q.v < pts[b].v ? i : b), 0);
    const iMax = pts.reduce((b, q, i) => (q.v > pts[b].v ? i : b), 0);
    g.font = '11px system-ui, sans-serif';
    g.textAlign = 'center';
    for (const [i, above] of [[iMax, true], [iMin, false]]) {
      const half = g.measureText(this.format(pts[i].v)).width / 2 + 2;
      const cx = Math.min(Math.max(this.x(i), half), this.width - half);
      const cy = this.y(pts[i].v);
      const color = this.kind === KIND.PRESSURE ? PRESSURE_LINE : this.kind === KIND.WIND ? WIND_LINE : (above ? MAX_PILL : MIN_PILL);
      g.fillStyle = css(color);
      g.beginPath(); g.arc(this.x(i), cy, 4, 0, 2 * Math.PI); g.fill();
      g.fillText(this.format(pts[i].v), cx, above ? cy - 8 : cy + 16);
    }

    // Arrows (wind), the hour axis and "now".
    const axisY = bottom + 18 + (this.kind === KIND.WIND ? ARROWS : 0);
    if (this.kind === KIND.WIND) this.drawArrows(g);
    g.font = '10px system-ui, sans-serif';
    g.fillStyle = css(p.textSecondary);
    for (let i = 0; i < n; i++) {
      const lp = parts(pts[i].t, this.tz);
      if (lp.minute === 0 && lp.hour % 6 === 0) g.fillText(String(lp.hour).padStart(2, '0'), this.x(i), axisY);
    }
    g.textAlign = 'right';
    g.fillStyle = css(p.accent);
    g.fillText(lang === 'it' ? 'ora' : 'now', this.width - 2, axisY);

    if (this.cursor >= 0 && this.cursor < n) this.drawCursor(g);
  }

  drawRain(g) {
    const n = this.points.length;
    if (!this.rain.some((mm) => (mm ?? 0) >= 0.05)) return;
    const scale = Math.max(2, ...this.rain.filter((x) => x != null));
    const hourPx = this.x(1) - this.x(0); // the points are an hour apart
    g.fillStyle = withAlpha(RAIN_COLOR, 0.75);
    for (let i = 0; i < n; i++) {
      const mm = this.rain[i];
      if (mm == null || mm < 0.05) continue;
      const right = this.x(i);
      const left = Math.max(LEFT, right - hourPx);
      if (right <= left) continue;
      const top = this.bottom - (mm / scale) * 42 - 1;
      g.fillRect(left + 0.5, top, right - left - 1, this.bottom - top);
    }
  }

  drawGusts(g) {
    g.beginPath();
    let open = false;
    for (let i = 0; i < this.points.length; i++) {
      const v = this.gust[i];
      if (v == null) { open = false; continue; }
      if (open) g.lineTo(this.x(i), this.y(v)); else g.moveTo(this.x(i), this.y(v));
      open = true;
    }
    g.strokeStyle = WIND_LINE;
    g.lineWidth = 1.8;
    g.lineCap = 'round';
    g.lineJoin = 'round';
    dash(g, 7, 5);
    g.stroke();
    dash(g, 1, 0);
    g.setLineDash([]);
  }

  /** One arrow every ~26 px under the chart: where the wind goes (the data say where it comes from). */
  drawArrows(g) {
    let lastX = -1e9;
    for (let i = 0; i < this.points.length; i++) {
      const from = this.dir[i];
      if (from == null) continue;
      const cx = this.x(i);
      if (cx - lastX < 26) continue;
      lastX = cx;
      arrow(g, cx, this.bottom + 14, from, WIND_LINE, 6, 1.6, 3.4);
    }
  }

  drawCursor(g) {
    const i = this.cursor;
    const p = this.p;
    const cx = this.x(i);
    g.strokeStyle = css(p.text); g.lineWidth = 1.5;
    g.beginPath(); g.moveTo(cx, TOP - 8); g.lineTo(cx, this.bottom); g.stroke();
    g.fillStyle = css(p.card);
    g.beginPath(); g.arc(cx, this.y(this.points[i].v), 5.5, 0, 2 * Math.PI); g.fill();
    g.lineWidth = 2.5; g.strokeStyle = css(p.text); g.stroke();
    if (this.kind === KIND.WIND && this.gust[i] != null) {
      g.fillStyle = WIND_LINE;
      g.beginPath(); g.arc(cx, this.y(this.gust[i]), 3.5, 0, 2 * Math.PI); g.fill();
    }
  }

  setCursor(i) {
    if (i === this.cursor) return false;
    this.cursor = i;
    this.draw();
    return true;
  }
}

/** An arrow centred on (cx, cy) pointing where the wind goes: [fromDegrees] is where it comes from. */
export function arrow(g, cx, cy, fromDegrees, color, len, width, head) {
  const a = ((fromDegrees + 180) % 360) * Math.PI / 180;
  const dx = Math.sin(a) * len;
  const dy = -Math.cos(a) * len;
  g.strokeStyle = typeof color === 'string' ? color : css(color);
  g.lineWidth = width;
  g.lineCap = 'round';
  g.beginPath(); g.moveTo(cx - dx, cy - dy); g.lineTo(cx + dx, cy + dy); g.stroke();
  for (const side of [-0.5, 0.5]) {
    const b = a + Math.PI + side;
    g.beginPath(); g.moveTo(cx + dx, cy + dy); g.lineTo(cx + dx + Math.sin(b) * head, cy + dy - Math.cos(b) * head); g.stroke();
  }
}

// ------------------------------------------------------------------------------------------------ meteogram

export const HOUR_PX = 8;
const M = { header: 38, icons: 26, temp: 142, rain: 46, wind: 28, axis: 18 };
const PAST_ALPHA = 0.43;

/**
 * The week at a glance: day headers, weather symbols and wind arrows every 3 hours, the temperature curve over
 * day and night with the pressure (violet) and the mean wind (azure) as thin lines on scales of their own, and the
 * rain bars. Starts 24 hours before the current hour (the past is dimmed).
 */
export class Meteogram {
  constructor(canvas) { this.canvas = canvas; this.slots = []; this.cursor = -1; this.elapsed = 0; }

  get height() { return M.header + M.icons + M.temp + M.rain + M.wind + M.axis; }
  get width() { return 2 * 4 + Math.max(this.slots.length, 24) * HOUR_PX; }

  /** @param slots hours { t, temp, felt, rain, prob, code, wind, dir, gust, pressure, isDay } from 24 h before now. */
  show({ slots, days, tz, now, palette, symbol }) {
    Object.assign(this, { slots, days, tz, now, p: palette, symbol });
    const hour = Math.floor(now / 3600) * 3600;
    this.elapsed = Math.max(0, slots.findIndex((s) => s.t >= hour));
    this.cursor = -1;
    this.draw();
  }

  x(i) { return 4 + i * HOUR_PX; }
  indexAt(px) { return Math.max(0, Math.min(this.slots.length - 1, Math.floor((px - 4) / HOUR_PX))); }

  draw() {
    const s = this.slots;
    if (!s.length) return;
    const p = this.p;
    const g = prepare(this.canvas, this.width, this.height);
    const n = s.length;
    const tempTop = M.header + M.icons;
    const tempBottom = tempTop + M.temp;
    const rainTop = tempBottom;
    const rainBottom = rainTop + M.rain;
    const windTop = rainBottom;
    const windBottom = windTop + M.wind;
    const el = this.elapsed;
    const alphaOf = (i) => (i < el ? PAST_ALPHA : 1);

    // Night behind every panel.
    g.fillStyle = css(nightShade(p));
    for (let i = 0; i < n; i++) if (s[i].isDay === false) g.fillRect(this.x(i), M.header, HOUR_PX, windBottom - M.header);

    // Day headers and the separators at midnight.
    g.textAlign = 'center';
    const dayNames = new Intl.DateTimeFormat(lang === 'it' ? 'it-IT' : 'en-GB', { weekday: 'short', day: 'numeric', timeZone: this.tz });
    let start = 0;
    for (let i = 1; i <= n; i++) {
      if (i === n || parts(s[i].t, this.tz).hour === 0 && parts(s[i].t, this.tz).minute === 0) {
        const date = this.dateKey(s[start].t);
        const day = this.days.find((d) => d.date === date);
        const mid = (this.x(start) + this.x(i)) / 2;
        g.globalAlpha = start < el ? 0.75 : 1;
        g.fillStyle = css(p.text);
        g.font = '600 12px system-ui, sans-serif';
        g.fillText(dayNames.format(new Date(s[start].t * 1000)).replace('.', ''), mid, 15);
        if (day && day.tmax != null) {
          g.font = '11px system-ui, sans-serif';
          g.fillStyle = css(MAX_PILL); g.textAlign = 'right'; g.fillText(`${Math.round(day.tmax)}°`, mid - 2, 31);
          g.fillStyle = css(MIN_PILL); g.textAlign = 'left'; g.fillText(`${Math.round(day.tmin)}°`, mid + 2, 31);
          g.textAlign = 'center';
        }
        g.globalAlpha = 1;
        if (i < n) {
          g.strokeStyle = css(blend(p.card, p.text, 0.22)); g.lineWidth = 1;
          g.beginPath(); g.moveTo(this.x(i), 0); g.lineTo(this.x(i), windBottom); g.stroke();
        }
        start = i;
      }
    }

    // Weather symbols every 3 hours.
    g.font = '16px system-ui, "Apple Color Emoji", "Noto Color Emoji", sans-serif';
    g.textAlign = 'center';
    for (const range of this.blocks()) {
      const mid = s[Math.min(range[1] - 1, range[0] + Math.floor((range[1] - range[0]) / 2))];
      if (mid.code == null) continue;
      g.globalAlpha = range[1] <= el ? PAST_ALPHA : 1;
      g.fillText(this.symbol(mid.code), (this.x(range[0]) + this.x(range[1])) / 2, M.header + 19);
    }
    g.globalAlpha = 1;

    // Pressure: a thin violet line, on a scale of its own (at least 8 mbar tall).
    const prs = s.map((q) => q.pressure).filter((v) => v != null);
    if (prs.length >= 2) {
      const pLo = Math.floor(Math.min(...prs));
      const pHi = Math.max(Math.ceil(Math.max(...prs)), pLo + 8);
      this.polyline(g, (i) => s[i].pressure, (v) => tempBottom - 14 - ((v - pLo) / (pHi - pLo)) * (M.temp - 34), PRESSURE_LINE, 1.1, alphaOf);
    }
    // Mean wind: a thin azure line (at least 20 km/h tall).
    const winds = s.map((q) => q.wind).filter((v) => v != null);
    const wHi = Math.max(Math.ceil(Math.max(0, ...winds)), 20);
    if (winds.length >= 2) {
      this.polyline(g, (i) => s[i].wind, (v) => tempBottom - 14 - (v / wHi) * (M.temp - 34), WIND_LINE, 1.8, alphaOf);
    }

    // Temperature curve, coloured by value, with a soft fill.
    const temps = s.map((q) => q.temp ?? NaN);
    const known = temps.filter((v) => !Number.isNaN(v));
    const lo = Math.floor(Math.min(...known) - 1);
    const hi = Math.ceil(Math.max(...known) + 1);
    const ty = (v) => tempBottom - 8 - ((v - lo) / Math.max(hi - lo, 4)) * (M.temp - 24);
    g.beginPath();
    g.moveTo(this.cx(0), tempBottom);
    for (let i = 0; i < n; i++) if (!Number.isNaN(temps[i])) g.lineTo(this.cx(i), ty(temps[i]));
    g.lineTo(this.cx(n - 1), tempBottom);
    g.closePath();
    g.fillStyle = withAlpha(p.text, 0.07);
    g.fill();
    g.lineWidth = 2.5; g.lineCap = 'round';
    for (let i = 0; i < n - 1; i++) {
      if (Number.isNaN(temps[i]) || Number.isNaN(temps[i + 1])) continue;
      g.globalAlpha = alphaOf(i);
      g.strokeStyle = css(temperatureColor((temps[i] + temps[i + 1]) / 2));
      g.beginPath(); g.moveTo(this.cx(i), ty(temps[i])); g.lineTo(this.cx(i + 1), ty(temps[i + 1])); g.stroke();
    }
    g.globalAlpha = 1;
    // Labels of the temperature at the day's extremes would crowd it: the field below the chart reads them out.

    // Rain: bars in mm (a scale of at least 2 mm/h), more opaque when more likely.
    const maxRain = Math.max(2, ...s.map((q) => q.rain ?? 0));
    for (let i = 0; i < n; i++) {
      const mm = s[i].rain ?? 0;
      if (mm < 0.05) continue;
      g.globalAlpha = i < el ? PAST_ALPHA : Math.min(1, Math.max(0.35, 0.35 + 0.65 * (s[i].prob ?? 50) / 100));
      g.fillStyle = RAIN_COLOR;
      const top = rainBottom - (mm / maxRain) * (M.rain - 4);
      g.fillRect(this.x(i) + 0.5, top, HOUR_PX - 1, rainBottom - top);
    }
    g.globalAlpha = 1;
    g.strokeStyle = css(blend(p.card, p.text, 0.15)); g.lineWidth = 1;
    g.beginPath(); g.moveTo(0, rainBottom); g.lineTo(this.width, rainBottom); g.stroke();

    // Wind every 3 hours: an arrow where it blows to; stronger winds brighter.
    for (const range of this.blocks()) {
      const block = s.slice(range[0], range[1]).filter((q) => q.wind != null && q.dir != null);
      if (!block.length) continue;
      const mid = block[Math.floor(block.length / 2)];
      const speed = Math.max(...block.map((q) => q.wind));
      g.globalAlpha = range[1] <= el ? PAST_ALPHA : 1;
      arrow(g, (this.x(range[0]) + this.x(range[1])) / 2, windTop + M.wind / 2, mid.dir, windColor(speed, p), 6, 1.4, 3.2);
    }
    g.globalAlpha = 1;

    // Hour axis and "now".
    g.font = '10px system-ui, sans-serif'; g.textAlign = 'center'; g.fillStyle = css(p.textSecondary);
    for (let i = 0; i < n; i++) {
      const lp = parts(s[i].t, this.tz);
      if (lp.hour % 6 === 0 && lp.hour !== 0 && lp.minute === 0) g.fillText(String(lp.hour).padStart(2, '0'), this.x(i), windBottom + 13);
    }
    g.strokeStyle = css(p.accent); g.lineWidth = 2;
    const nowX = this.x(el) + ((this.now - s[el].t) / 3600) * HOUR_PX;
    g.beginPath(); g.moveTo(nowX, M.header - 2); g.lineTo(nowX, windBottom); g.stroke();

    if (this.cursor >= 0 && this.cursor < n) {
      const i = this.cursor;
      const cx = this.cx(i);
      g.strokeStyle = css(p.text); g.lineWidth = 1.5;
      g.beginPath(); g.moveTo(cx, M.header); g.lineTo(cx, windBottom); g.stroke();
      if (!Number.isNaN(temps[i])) {
        g.fillStyle = css(p.card); g.beginPath(); g.arc(cx, ty(temps[i]), 5.5, 0, 2 * Math.PI); g.fill();
        g.lineWidth = 2.5; g.stroke();
      }
      if (prs.length >= 2 && s[i].pressure != null) {
        const pLo = Math.floor(Math.min(...prs)); const pHi = Math.max(Math.ceil(Math.max(...prs)), pLo + 8);
        g.fillStyle = PRESSURE_LINE; g.beginPath(); g.arc(cx, tempBottom - 14 - ((s[i].pressure - pLo) / (pHi - pLo)) * (M.temp - 34), 3.5, 0, 2 * Math.PI); g.fill();
      }
      if (winds.length >= 2 && s[i].wind != null) {
        g.fillStyle = WIND_LINE; g.beginPath(); g.arc(cx, tempBottom - 14 - (s[i].wind / wHi) * (M.temp - 34), 3.5, 0, 2 * Math.PI); g.fill();
      }
    }
  }

  cx(i) { return this.x(i) + HOUR_PX / 2; }

  polyline(g, valueOf, yOf, color, width, alphaOf) {
    g.lineWidth = width; g.lineCap = 'round'; g.strokeStyle = color;
    for (let i = 0; i < this.slots.length - 1; i++) {
      const a = valueOf(i); const b = valueOf(i + 1);
      if (a == null || b == null) continue;
      g.globalAlpha = alphaOf(i) === 1 ? 0.9 : alphaOf(i);
      g.beginPath(); g.moveTo(this.cx(i), yOf(a)); g.lineTo(this.cx(i + 1), yOf(b)); g.stroke();
    }
    g.globalAlpha = 1;
  }

  dateKey(t) {
    const p = parts(t, this.tz);
    return `${p.year}-${String(p.month).padStart(2, '0')}-${String(p.day).padStart(2, '0')}`;
  }

  /** The 3-hour blocks of the symbol and wind rows: [from, to) slot ranges starting at each hour divisible by 3. */
  blocks() {
    const starts = [];
    this.slots.forEach((q, i) => { if (parts(q.t, this.tz).hour % 3 === 0) starts.push(i); });
    if (!starts.length || starts[0] !== 0) starts.unshift(0);
    return starts.map((from, k) => [from, starts[k + 1] ?? this.slots.length]);
  }

  setCursor(i) {
    if (i === this.cursor) return false;
    this.cursor = i;
    this.draw();
    return true;
  }

  /** The slot read out without a cursor: the current hour. */
  get nowSlot() { return this.elapsed; }
}

export { minutesOfDay };
