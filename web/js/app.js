import { t, lang, locale } from './i18n.js';
import { Store, ACCENTS, CURVES } from './store.js';
import { THEMES, GROUP_TITLES } from './themes.js';
import { exportBackup, importBackup } from './backup.js';
import { fetchForecast, parseForecast, last24Temperature, last24Hours, describeCode, searchPlaces, matches } from './weather.js';
import * as sun from './astro/sun.js';
import * as moon from './astro/moon.js';
import { dateOf, addDays, hhmm, parts, startOfDay } from './tz.js';
import { css, blend, readable, luminance, temperatureColor, PRESSURE_LINE, WIND_LINE, MAX_PILL, MIN_PILL, windColor } from './color.js';
import { Last24Chart, Meteogram, KIND, HOUR_PX } from './charts.js';
import { drawSunCurve, drawMoon } from './sunmoon.js';

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const nowSec = () => Math.floor(Date.now() / 1000);

const store = new Store();
let forecast = null;
let palette = null;
let sunDate = null; // 'YYYY-MM-DD' of the Sun and Moon card
let hourlyDay = null;
let charts = null;
let error = null;

const place = () => store.place;
const tz = () => forecast?.tz || Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
const compassName = (deg) => t.compass[Math.floor((((deg % 360) + 360) % 360 + 22.5) / 45) % 8];
const windFrom = (deg) => t.windFrom(compassName(deg), Math.round(deg));

// ------------------------------------------------------------------------------------------------ theme

export function currentPalette() {
  const theme = THEMES.find((x) => x.key === store.theme) || THEMES[0];
  const accentHex = (ACCENTS.find((a) => a[0] === store.accent) || ACCENTS[0])[2];
  return { ...theme, accent: readable(accentHex, theme.light), curve: store.curve };
}

function applyTheme() {
  palette = currentPalette();
  const r = document.documentElement.style;
  r.setProperty('--page', palette.page); r.setProperty('--card', palette.card); r.setProperty('--text', palette.text);
  r.setProperty('--text2', palette.textSecondary); r.setProperty('--dim', palette.dim); r.setProperty('--accent', palette.accent);
  document.documentElement.dataset.light = palette.light ? '1' : '0';
  document.querySelector('meta[name=theme-color]').setAttribute('content', palette.page);
}

// ------------------------------------------------------------------------------------------------ texts

function setStaticTexts() {
  document.documentElement.lang = lang;
  document.title = t.appTitle;
  $('settingsBtn').setAttribute('aria-label', t.settings);
  $('sunmoonTitle').textContent = t.sunMoon;
  $('weatherTitle').textContent = t.weather;
  $('hourlyTitle').textContent = t.hourly;
  $('radarTitle').textContent = t.radar;
  $('radarNote').textContent = t.radarNote;
  $('step1').textContent = t.everyHour; $('step3').textContent = t.every3Hours;
  $('c1Title').textContent = t.last24; $('c2Title').textContent = t.pressure24; $('c3Title').textContent = t.wind24; $('mgTitle').textContent = t.meteogram;
  $('c1Step').textContent = ''; $('c2Step').textContent = `${t.pressureUnit} · ${t.everyHour}`; $('c3Step').textContent = `km/h · ${t.everyHour}`;
  $('c2Legend').textContent = t.rainLegend; $('c3Legend').textContent = t.gustLegend;
  $('searchTitle').textContent = t.search; $('searchInput').placeholder = t.searchHint; $('searchGo').textContent = t.search; $('searchCancel').textContent = t.cancel;
  $('pickedMessage').textContent = t.pickedMessage; $('pickedJust').textContent = t.justUse; $('pickedAdd').textContent = t.addFavorite;
  $('settingsTitle').textContent = t.settings;
}

// ------------------------------------------------------------------------------------------------ header

function showHeader() {
  const p = place();
  $('place').textContent = (p.automatic ? t.deviceLocation.replace('◎ ', '') : (p.name || `${p.lat.toFixed(4)}, ${p.lon.toFixed(4)}`)) + ' ▾';
  const d = new Date();
  const zone = tz();
  const full = new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'long', timeZone: zone }).format(d);
  const short = new Intl.DateTimeFormat(locale, { weekday: 'short', day: 'numeric', month: 'short', timeZone: zone }).format(d).replace('.', '');
  const numeric = new Intl.DateTimeFormat(locale, { day: '2-digit', month: '2-digit', timeZone: zone }).format(d);
  const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);
  const el = $('today');
  el.textContent = cap(full);
  // The full date when it fits, the short one when it does not, then dd/mm (a long place name takes the room).
  requestAnimationFrame(() => {
    for (const text of [cap(full), cap(short)]) {
      el.textContent = text;
      if (el.scrollWidth <= el.clientWidth) return;
    }
    el.textContent = numeric;
  });
}

// ------------------------------------------------------------------------------------------------ Sun and Moon

function showSunMoon() {
  const p = place();
  const zone = tz();
  const today = dateOf(nowSec(), zone);
  sunDate = sunDate || today;
  $('dayLabel').textContent = sunDate === today ? t.today : new Intl.DateTimeFormat(locale, { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' }).format(new Date(sunDate + 'T12:00:00Z'));
  const width = $('sunmoon').clientWidth - 28;
  drawSunCurve($('sunCurve'), { date: sunDate, lat: p.lat, lon: p.lon, tz: zone, palette, style: store.curve, width, now: nowSec() });

  const d = sun.day(sunDate, p.lat, p.lon, zone);
  const rows = [];
  const time = (e) => (e == null ? '—' : hhmm(e, zone));
  const dur = (s) => `${Math.floor(s / 3600)} h ${String(Math.floor((s % 3600) / 60)).padStart(2, '0')} min`;
  if (d.kind === 'normal') rows.push([t.sunrise, time(d.sunrise)], [t.solarNoon, time(d.solarNoon)], [t.sunset, time(d.sunset)], [t.dayLength, dur(d.length)]);
  else rows.push([d.kind === 'polarDay' ? t.polarDay : t.polarNight, ''], [t.solarNoon, time(d.solarNoon)]);
  for (const [label, alt] of [[t.civil, -6], [t.nautical, -12], [t.astronomical, -18]]) {
    const c = sun.crossing(sunDate, p.lat, p.lon, zone, alt);
    rows.push([label, `${time(c.morning)} · ${time(c.evening)}`]);
  }
  const blue = sun.crossing(sunDate, p.lat, p.lon, zone, -4);
  const gold = sun.crossing(sunDate, p.lat, p.lon, zone, 6);
  rows.push([t.blueHour, `${time(blue.morning)} · ${time(blue.evening)}`], [t.goldenHour, `${time(gold.morning)} · ${time(gold.evening)}`]);
  $('sunTimes').innerHTML = rows.map(([k, v]) => `<div><dt>${esc(k)}</dt><dd>${esc(v)}</dd></div>`).join('');

  // The Moon at midday of the chosen day.
  const noon = sun.day(sunDate, p.lat, p.lon, zone).solarNoon;
  const ph = moon.phase(noon);
  const size = 84;
  drawMoon($('moonDisc'), { illumination: ph.illumination, waxing: ph.waxing, southern: p.lat < 0, palette, size });
  const rs = moon.riseSet(sunDate, p.lat, p.lon, zone);
  const next = moon.nextQuarters(noon).map((q) => `${t.quarters[q.key]} ${new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: zone }).format(new Date(q.time * 1000))}`);
  $('moonInfo').innerHTML = `<b>${esc(t.phases[ph.name])}</b><span>${esc(t.illumination)} ${(ph.illumination * 100).toFixed(0)} %</span>` +
    `<span>${esc(t.moonrise)} ${time(rs.rise)} · ${esc(t.moonset)} ${time(rs.set)}</span>` +
    `<span class="muted small">${esc(t.nextPhases)}: ${next.map(esc).join(' · ')}</span>`;
}

// ------------------------------------------------------------------------------------------------ weather

const symbolFor = (code, isDay) => (isDay === false && code != null && code <= 1 ? '🌙' : describeCode(code, lang).symbol);
const deg1 = (v) => `${v.toFixed(1)}°`;
const deg0 = (v) => `${Math.round(v)}°`;
const weekday = (date) => new Intl.DateTimeFormat(locale, { weekday: 'short', timeZone: 'UTC' }).format(new Date(date + 'T12:00:00Z')).replace('.', '');
const ARROWS = ['↑', '↗', '→', '↘', '↓', '↙', '←', '↖'];
const arrowChar = (from) => ARROWS[Math.floor(((((from + 180) % 360) + 360) % 360 + 22.5) / 45) % 8];

function showWeather() {
  const f = forecast;
  if (!f) { $('nowBox').innerHTML = `<span class="muted">${esc(error || t.loading)}</span>`; $('daysRow').innerHTML = ''; $('hourlyTable').innerHTML = ''; return; }
  const now = nowSec();
  const hour = Math.floor(now / 3600) * 3600;
  const cur = f.hours.find((h) => h.t === hour) || f.hours[0];
  const today = f.days.find((d) => d.date === dateOf(now, f.tz));
  $('nowBox').innerHTML = `<span class="sym">${symbolFor(cur.code, cur.isDay)}</span><span class="big">${cur.temp != null ? deg0(cur.temp) : '—'}</span>` +
    `<span class="meta"><span>${esc(describeCode(cur.code, lang).name)}${cur.felt != null ? ' · ' + esc(t.feels(deg0(cur.felt))) : ''}</span>` +
    `<span>${cur.wind != null ? `${arrowChar(cur.dir ?? 0)} ${Math.round(cur.wind)} km/h` : ''}${cur.gust != null ? ' · ' + esc(t.gust(Math.round(cur.gust))) : ''}</span>` +
    `<span>${today ? `<b style="color:${MAX_PILL}">${deg0(today.tmax)}</b> / <b style="color:${MIN_PILL}">${deg0(today.tmin)}</b>` : ''}${today && today.prob != null ? ` · ${esc(t.rain)} ${today.prob} %` : ''}</span></span>`;

  hourlyDay = hourlyDay && f.days.some((d) => d.date === hourlyDay) ? hourlyDay : f.days.find((d) => d.date === dateOf(now, f.tz))?.date ?? f.days[0]?.date;
  $('daysRow').innerHTML = f.days.map((d) => `<button class="day" role="tab" data-date="${d.date}" aria-selected="${d.date === hourlyDay}">` +
    `<span class="w">${esc(weekday(d.date))}</span><span class="s">${describeCode(d.code, lang).symbol}</span>` +
    `<span class="t"><b>${d.tmax != null ? deg0(d.tmax) : '—'}</b> <i>${d.tmin != null ? deg0(d.tmin) : '—'}</i></span></button>`).join('');
  showHourly();
}

function showHourly() {
  const f = forecast;
  if (!f) return;
  const step = store.stepHours;
  $('step1').setAttribute('aria-pressed', step === 1); $('step3').setAttribute('aria-pressed', step === 3);
  const hour = Math.floor(nowSec() / 3600) * 3600;
  const rows = f.hours.filter((h) => dateOf(h.t, f.tz) === hourlyDay && (step === 1 || parts(h.t, f.tz).hour % 3 === 2));
  $('hourlyTable').innerHTML = rows.map((h) => {
    const wet = (h.rain ?? 0) >= 0.05;
    const rainText = wet ? `${h.rain.toFixed(1)} mm` : '·';
    const probText = h.prob != null && h.t >= hour && (wet || h.prob >= 30) ? ` ${h.prob} %` : '';
    return `<tr class="${h.t < hour ? 'past' : ''}"><td class="time">${hhmm(h.t, f.tz)}</td>` +
      `<td><span class="pill" style="background:${h.temp != null ? css(temperatureColor(h.temp)) : 'transparent'}">${h.temp != null ? deg0(h.temp) : '—'}</span></td>` +
      `<td class="sym">${symbolFor(h.code, h.isDay)}</td><td class="felt" title="${h.felt != null ? esc(t.feels(deg0(h.felt))) : ''}">${h.felt != null ? `(${deg0(h.felt)})` : ''}</td>` +
      `<td class="rain">${rainText}${probText}</td>` +
      `<td class="wind" style="color:${h.wind != null ? css(windColor(h.wind, palette)) : 'inherit'}">${h.wind != null ? `${arrowChar(h.dir ?? 0)} ${Math.round(h.wind)}` : ''}${h.gust != null ? ` <span class="gust">${Math.round(h.gust)}</span>` : ''}</td></tr>`;
  }).join('');
}

// ------------------------------------------------------------------------------------------------ radar

function showRadar() {
  const p = place();
  const url = 'https://embed.windy.com/embed2.html?' + new URLSearchParams({
    lat: p.lat.toFixed(3), lon: p.lon.toFixed(3), detailLat: p.lat.toFixed(3), detailLon: p.lon.toFixed(3), width: '650', height: '450', zoom: '8',
    level: 'surface', overlay: 'radar', product: 'radar', menu: '', message: 'true', marker: 'true', calendar: 'now', pressure: '', type: 'map',
    location: 'coordinates', detail: '', metricWind: 'km/h', metricTemp: '°C', radarRange: '-1',
  });
  if ($('radar').dataset.src !== url) { $('radar').dataset.src = url; $('radar').src = url; }
}

// ------------------------------------------------------------------------------------------------ readouts and tiles

function setReadout(el, { when, values, detail }) {
  el.innerHTML = `<div class="l1">${esc(when)}</div><div class="l2">${values.map((v, i) => (i ? '<span class="sep"> · </span>' : '') + `<span style="color:${css(v.color)}">${esc(v.text)}</span>`).join('')}</div><div class="l3">${esc(detail || '')}</div>`;
}

function tiles(el, kind, pts, trendMinutes) {
  const fmt = (v) => (kind === KIND.WIND ? `${v.toFixed(0)}` : kind === KIND.PRESSURE ? v.toFixed(1) : deg1(v));
  const iMin = pts.reduce((b, q, i) => (q.v < pts[b].v ? i : b), 0);
  const iMax = pts.reduce((b, q, i) => (q.v > pts[b].v ? i : b), 0);
  const last = pts.length - 1;
  const when = (e) => new Intl.DateTimeFormat(locale, { weekday: 'short', hour: '2-digit', minute: '2-digit', timeZone: tz() }).format(new Date(e * 1000)).replace('.', '');
  const earlier = (() => { for (let i = last; i >= 0; i--) if (pts[last].t - pts[i].t >= trendMinutes * 60) return i; return -1; })();
  const delta = earlier >= 0 ? pts[last].v - pts[earlier].v : null;
  const sign = (v) => (v >= 0 ? '+' : '−') + fmt(Math.abs(v));
  const third = kind === KIND.WIND ? [t.avg, fmt(pts.reduce((s, q) => s + q.v, 0) / pts.length), t.in24h] : [t.range, fmt(pts[iMax].v - pts[iMin].v), t.in24h];
  const colorOf = (which) => (kind === KIND.TEMPERATURE ? (which === 'min' ? MIN_PILL : MAX_PILL) : 'inherit');
  const items = [
    [t.min, fmt(pts[iMin].v), when(pts[iMin].t), colorOf('min')],
    [t.max, fmt(pts[iMax].v), when(pts[iMax].t), colorOf('max')],
    [third[0], third[1], third[2], 'inherit'],
    [kind === KIND.PRESSURE ? t.last3h : t.lastHour, delta == null ? '—' : sign(delta), t.nowValue(fmt(pts[last].v)), 'inherit'],
  ];
  el.innerHTML = items.map(([k, v, n, c]) => `<div class="tile"><div class="k">${esc(k)}</div><div class="v" style="color:${typeof c === 'string' && c.startsWith('#') ? c : 'inherit'}">${esc(v)}</div><div class="n">${esc(n)}</div></div>`).join('');
}

const clockText = (e, label) => `${new Intl.DateTimeFormat(locale, { weekday: 'short', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', timeZone: tz() }).format(new Date(e * 1000)).replace(/\./g, '').replace(/^./, (c) => c.toUpperCase())} · ${label}`;

// ------------------------------------------------------------------------------------------------ the 24-hour charts

function bindPointer(canvas, chart, onMove) {
  const at = (ev) => {
    const r = canvas.getBoundingClientRect();
    return ev.clientX - r.left;
  };
  canvas.addEventListener('pointerdown', (ev) => onMove(at(ev)));
  canvas.addEventListener('pointermove', (ev) => { if (ev.pointerType !== 'mouse' || ev.buttons || true) onMove(at(ev)); });
  canvas.addEventListener('pointerleave', (ev) => { if (ev.pointerType === 'mouse') onMove(null); });
}

function showCharts() {
  const f = forecast;
  for (const id of ['c1', 'c2', 'c3', 'mgCard']) $(id).hidden = !f;
  if (!f) return;
  const now = nowSec();
  const zone = f.tz;
  const p = place();
  const isDay = (e) => sun.elevation(e, p.lat, p.lon) > sun.HORIZON_DEG;
  const width = $('c1').clientWidth - 28;
  const hourOf = (e) => Math.floor(e / 3600) * 3600;
  const rows = last24Hours(f, now);
  const byHour = new Map(f.hours.map((h) => [h.t, h]));

  // --- temperature
  const tp = last24Temperature(f, now).map((q) => ({ t: q.t, v: q.temp }));
  $('c1').hidden = tp.length < 2;
  if (tp.length >= 2) {
    $('c1Step').textContent = f.fine.length ? (lang === 'it' ? 'ogni 15 minuti' : 'every 15 minutes') : t.everyHour;
    const chart = new Last24Chart($('c1Canvas'), KIND.TEMPERATURE);
    chart.show({ points: tp, tz: zone, isDay, palette, width });
    tiles($('c1Tiles'), KIND.TEMPERATURE, tp, 60);
    const read = (i) => {
      const k = i >= 0 ? i : tp.length - 1;
      const h = byHour.get(hourOf(tp[k].t));
      let earlier = -1; for (let j = k; j >= 0; j--) if (tp[k].t - tp[j].t >= 3600) { earlier = j; break; }
      const trend = earlier >= 0 ? `${t.lastHour}: ${tp[k].v - tp[earlier].v >= 0 ? '+' : '−'}${Math.abs(tp[k].v - tp[earlier].v).toFixed(1)}°` : null;
      setReadout($('c1Readout'), { when: clockText(tp[k].t, t.past), values: [{ text: deg1(tp[k].v), color: temperatureColor(tp[k].v) }],
        detail: [h?.felt != null ? t.feels(deg0(h.felt)) : null, trend].filter(Boolean).join(' · ') });
    };
    read(-1);
    bindPointer($('c1Canvas'), chart, (x) => { const i = x == null ? -1 : chart.indexAt(x); if (chart.setCursor(i)) read(i); });
  }

  // --- pressure with the rain bars
  const pr = rows.filter((h) => h.pressure != null);
  $('c2').hidden = pr.length < 2;
  if (pr.length >= 2) {
    const pts = pr.map((h) => ({ t: h.t, v: h.pressure }));
    const chart = new Last24Chart($('c2Canvas'), KIND.PRESSURE);
    chart.show({ points: pts, rain: pr.map((h) => h.rain), tz: zone, isDay, palette, width });
    tiles($('c2Tiles'), KIND.PRESSURE, pts, 180);
    const read = (i) => {
      const k = i >= 0 ? i : pts.length - 1;
      const h = pr[k];
      const rain = [h.rain != null ? ((h.rain < 0.05) ? '0 mm' : `${h.rain.toFixed(1)} mm`) : null, h.prob != null ? `${h.prob} %` : null].filter(Boolean).join(' · ');
      setReadout($('c2Readout'), { when: clockText(h.t, t.past), values: [{ text: `${h.pressure.toFixed(1)} ${t.pressureUnit}`, color: PRESSURE_LINE }], detail: rain ? `${t.rain} ${rain}` : '' });
    };
    read(-1);
    bindPointer($('c2Canvas'), chart, (x) => { const i = x == null ? -1 : chart.indexAt(x); if (chart.setCursor(i)) read(i); });
  }

  // --- wind with the gusts and the arrows
  const wd = rows.filter((h) => h.wind != null);
  $('c3').hidden = wd.length < 2;
  if (wd.length >= 2) {
    const pts = wd.map((h) => ({ t: h.t, v: h.wind }));
    const chart = new Last24Chart($('c3Canvas'), KIND.WIND);
    chart.show({ points: pts, dir: wd.map((h) => h.dir), gust: wd.map((h) => h.gust), tz: zone, isDay, palette, width });
    tiles($('c3Tiles'), KIND.WIND, pts, 60);
    const read = (i) => {
      const k = i >= 0 ? i : pts.length - 1;
      const h = wd[k];
      setReadout($('c3Readout'), { when: clockText(h.t, t.past), values: [{ text: `${Math.round(h.wind)} km/h`, color: WIND_LINE }],
        detail: [h.gust != null ? t.gust(Math.round(h.gust)) : null, h.dir != null ? windFrom(h.dir) : null].filter(Boolean).join(' · ') });
    };
    read(-1);
    bindPointer($('c3Canvas'), chart, (x) => { const i = x == null ? -1 : chart.indexAt(x); if (chart.setCursor(i)) read(i); });
  }

  // --- meteogram, from 24 hours before the current hour
  const from = hourOf(now) - 24 * 3600;
  const slots = f.hours.filter((h) => h.t >= from).map((h) => ({ ...h, isDay: h.isDay ?? isDay(h.t) }));
  const mg = new Meteogram($('mgCanvas'));
  mg.show({ slots, days: f.days, tz: zone, now, palette, symbol: (c) => describeCode(c, lang).symbol });
  const readM = (i) => {
    const k = i >= 0 ? i : mg.nowSlot;
    const s = slots[k];
    const values = [];
    if (s.temp != null) values.push({ text: deg1(s.temp), color: temperatureColor(s.temp) });
    if (s.wind != null) values.push({ text: `${Math.round(s.wind)} km/h`, color: WIND_LINE });
    if (s.pressure != null) values.push({ text: `${Math.round(s.pressure)} ${t.pressureUnit}`, color: PRESSURE_LINE });
    const rain = (s.rain ?? 0) < 0.05 ? '0 mm' : `${s.rain.toFixed(1)} mm`;
    const label = k < mg.elapsed ? t.past : k === mg.elapsed ? t.nowLabel : t.forecast;
    setReadout($('mgReadout'), { when: clockText(s.t, label), values, detail: `${t.rain} ${rain}${s.prob != null && k >= mg.elapsed ? ` · ${s.prob} %` : ''}` });
  };
  readM(-1);
  const canvas = $('mgCanvas');
  canvas.onpointermove = (ev) => { if (ev.pointerType === 'mouse') { const r = canvas.getBoundingClientRect(); const i = mg.indexAt(ev.clientX - r.left); if (mg.setCursor(i)) readM(i); } };
  canvas.onpointerleave = (ev) => { if (ev.pointerType === 'mouse' && mg.setCursor(-1)) readM(-1); };
  canvas.onclick = (ev) => {
    const r = canvas.getBoundingClientRect();
    const i = mg.indexAt(ev.clientX - r.left);
    if (ev.clientY - r.top < 38) { // a tap on a day header opens that day in the hour-by-hour table
      hourlyDay = dateOf(slots[i].t, zone);
      showWeather();
      $('hourlyCard').scrollIntoView({ behavior: 'smooth', block: 'start' });
      return;
    }
    if (mg.setCursor(mg.cursor === i ? -1 : i)) readM(mg.cursor);
  };
  $('mgScroll').scrollLeft = 0; // from the start of the chart: yesterday, with "now" further right
}

// ------------------------------------------------------------------------------------------------ places

function closeMenu() { $('placeMenu').hidden = true; }

function openPlaceMenu() {
  const menu = $('placeMenu');
  const cur = place();
  const favs = store.favorites;
  const item = (label, action) => `<button role="menuitem" data-action="${action}">${esc(label)}</button>`;
  menu.innerHTML = item(t.search, 'search') + '<hr>' + item(t.deviceLocation, 'device') + '<hr>' +
    (favs.length ? favs.map((f, i) => item((!cur.automatic && Math.abs(f.lat - cur.lat) < 1e-4 && Math.abs(f.lon - cur.lon) < 1e-4 ? '★ ' : '') + f.name, 'fav' + i)).join('') : item(t.noFavorites, 'settings'));
  const r = $('place').getBoundingClientRect();
  menu.style.left = Math.max(8, r.left + window.scrollX) + 'px';
  menu.style.top = (r.bottom + window.scrollY + 4) + 'px';
  menu.hidden = false;
}

async function usePlace(p) {
  store.place = p;
  forecast = null; error = null; sunDate = null;
  renderAll();
  await loadForecast();
}

function useDevice() {
  if (!navigator.geolocation) { $('status').textContent = t.geoUnsupported; return; }
  navigator.geolocation.getCurrentPosition(
    (pos) => usePlace({ lat: pos.coords.latitude, lon: pos.coords.longitude, name: null, automatic: true }),
    () => { $('status').textContent = t.geoDenied; },
    { timeout: 15000, maximumAge: 600000 },
  );
}

function openSearch() {
  const dlg = $('searchDialog');
  $('searchInput').value = '';
  renderSearchBody();
  dlg.showModal();
  $('searchInput').focus();
}

function renderSearchBody(results, message) {
  const body = $('searchBody');
  const recents = store.recents;
  let html = '';
  if (message) html += `<p class="muted small">${esc(message)}</p>`;
  if (results) {
    html += `<div class="label">${esc(t.choosePlace)}</div><ul>${results.map((r, i) => `<li><button type="button" data-result="${i}">${esc(r.detail)}</button></li>`).join('')}</ul>`;
  } else if (recents.length) {
    html += `<div class="label">${esc(t.recents)}</div><ul>${recents.map((r, i) => `<li><button type="button" data-recent="${i}">${esc(r.name)}</button></li>`).join('')}</ul>`;
  }
  body.innerHTML = html;
  body._results = results;
}

async function runSearch() {
  const q = $('searchInput').value.trim();
  if (!q) return;
  try {
    const results = await searchPlaces(q, lang);
    if (!results.length) { renderSearchBody(null, t.noResults); return; }
    // One result that is what was asked for is taken at once; anything else is offered to choose from.
    if (results.length === 1 && matches(q, results[0])) { picked(results[0]); return; }
    renderSearchBody(results);
  } catch {
    renderSearchBody(null, t.searchFailed);
  }
}

/** A place found by name: used right away, remembered among the recents, then offered for the favourites. */
function picked(r) {
  $('searchDialog').close();
  const p = { lat: r.lat, lon: r.lon, name: r.name };
  store.addRecent(p);
  usePlace({ ...p, automatic: false });
  $('pickedTitle').textContent = r.name;
  const dlg = $('pickedDialog');
  $('pickedAdd').onclick = () => { store.addFavorite(p); $('status').textContent = t.favoriteAdded(r.name); dlg.close(); };
  $('pickedJust').onclick = () => dlg.close();
  dlg.showModal();
}

// ------------------------------------------------------------------------------------------------ settings

function openSettings() {
  const body = $('settingsBody');
  const swatch = (key, label, color, pressed, kind) => `<button type="button" data-${kind}="${esc(key)}" aria-pressed="${pressed}"><i style="background:${esc(color)}"></i>${esc(label)}</button>`;
  const order = ['LEGACY', 'SYSTEM', 'COMMUNITY_DARK', 'COMMUNITY_LIGHT', 'ORIGINAL_DARK', 'ORIGINAL_LIGHT', 'MINIMALIST'];
  const themes = order.map((g) => {
    const items = THEMES.filter((x) => x.group === g);
    return (t.groups[g] ? `<div class="setgroup">${esc(t.groups[g])}</div>` : '') +
      `<div class="sw">${items.map((x) => swatch(x.key, x.name, x.background, x.key === store.theme, 'theme')).join('')}</div>`;
  }).join('');
  body.innerHTML = `<div class="set"><h4>${esc(t.theme)}</h4>${themes}` +
    `<h4>${esc(t.accent)}</h4><div class="sw">${ACCENTS.map((a) => swatch(a[0], a[1], a[2], a[0] === store.accent, 'accent')).join('')}</div>` +
    `<h4>${esc(t.curve)}</h4>${CURVES.map((c) => `<label><input type="radio" name="curve" value="${c}" ${store.curve === c ? 'checked' : ''}> ${esc(t.curves[c])}</label>`).join('')}` +
    `<h4>${esc(t.backup)}</h4><div class="row"><button type="button" id="exportBtn">${esc(t.exportBtn)}</button><button type="button" id="importBtn">${esc(t.importBtn)}</button><input type="file" id="importFile" accept="application/json,.json" hidden></div></div>`;
  if (!$('settingsDialog').open) $('settingsDialog').showModal();
}

function download(name, text) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}

// ------------------------------------------------------------------------------------------------ data

const CACHE_KEY = 'forecast';

function cached() {
  const c = store.read(CACHE_KEY, null);
  const p = place();
  return c && Math.abs(c.lat - p.lat) < 0.02 && Math.abs(c.lon - p.lon) < 0.02 ? c.data : null;
}

async function loadForecast() {
  const p = place();
  const stale = cached();
  if (stale && !forecast) { forecast = stale; renderAll(); }
  try {
    const f = await fetchForecast(p.lat, p.lon);
    forecast = f; error = null;
    store.write(CACHE_KEY, { lat: p.lat, lon: p.lon, data: f });
    $('status').textContent = t.updated(hhmm(f.fetchedAt, f.tz));
  } catch (e) {
    error = t.loadFailed;
    if (!forecast) $('status').textContent = t.loadFailed;
  }
  renderAll();
}

function renderAll() {
  applyTheme();
  showHeader();
  showSunMoon();
  showWeather();
  showRadar();
  showCharts();
  if (!forecast && !error) $('status').textContent = t.loading;
}

// ------------------------------------------------------------------------------------------------ wiring

function wire() {
  $('place').addEventListener('click', (ev) => { ev.stopPropagation(); if ($('placeMenu').hidden) openPlaceMenu(); else closeMenu(); });
  document.addEventListener('click', (ev) => { if (!$('placeMenu').contains(ev.target)) closeMenu(); });
  $('placeMenu').addEventListener('click', (ev) => {
    const a = ev.target.closest('button')?.dataset.action;
    if (!a) return;
    closeMenu();
    if (a === 'search') openSearch();
    else if (a === 'device') useDevice();
    else if (a === 'settings') openSettings();
    else if (a.startsWith('fav')) { const f = store.favorites[Number(a.slice(3))]; if (f) usePlace({ lat: f.lat, lon: f.lon, name: f.name, automatic: false }); }
  });
  $('settingsBtn').addEventListener('click', openSettings);
  $('settingsClose').addEventListener('click', () => $('settingsDialog').close());

  $('searchForm').addEventListener('submit', (ev) => { ev.preventDefault(); runSearch(); });
  $('searchCancel').addEventListener('click', () => $('searchDialog').close());
  $('searchBody').addEventListener('click', (ev) => {
    const b = ev.target.closest('button');
    if (!b) return;
    if (b.dataset.recent != null) { const r = store.recents[Number(b.dataset.recent)]; if (r) picked({ name: r.name, lat: r.lat, lon: r.lon }); }
    if (b.dataset.result != null) { const r = $('searchBody')._results?.[Number(b.dataset.result)]; if (r) picked(r); }
  });

  $('dayPrev').addEventListener('click', () => { sunDate = addDays(sunDate, -1); showSunMoon(); });
  $('dayNext').addEventListener('click', () => { sunDate = addDays(sunDate, 1); showSunMoon(); });
  $('dayLabel').addEventListener('click', () => { sunDate = dateOf(nowSec(), tz()); showSunMoon(); });

  $('daysRow').addEventListener('click', (ev) => {
    const d = ev.target.closest('.day')?.dataset.date;
    if (d) { hourlyDay = d; showWeather(); }
  });
  $('step1').addEventListener('click', () => { store.stepHours = 1; showHourly(); });
  $('step3').addEventListener('click', () => { store.stepHours = 3; showHourly(); });

  $('settingsBody').addEventListener('click', (ev) => {
    const b = ev.target.closest('button');
    if (!b) return;
    if (b.dataset.theme) { store.theme = b.dataset.theme; renderAll(); openSettings(); }
    else if (b.dataset.accent) { store.accent = b.dataset.accent; renderAll(); openSettings(); }
    else if (b.id === 'exportBtn') download('alba-tramonto-impostazioni.json', exportBackup(store));
    else if (b.id === 'importBtn') $('importFile').click();
  });
  $('settingsBody').addEventListener('change', async (ev) => {
    if (ev.target.name === 'curve') { store.curve = ev.target.value; renderAll(); }
    if (ev.target.id === 'importFile' && ev.target.files[0]) {
      try {
        importBackup(store, await ev.target.files[0].text());
        sunDate = null; forecast = null;
        $('status').textContent = t.imported;
        renderAll(); openSettings(); loadForecast();
      } catch (e) { $('status').textContent = t.importFailed(e.message); }
    }
  });

  let resizeTimer;
  window.addEventListener('resize', () => { clearTimeout(resizeTimer); resizeTimer = setTimeout(() => { showSunMoon(); showCharts(); }, 150); });
  document.addEventListener('visibilitychange', () => { if (!document.hidden) { showHeader(); loadForecast(); } });
  setInterval(() => { if (!document.hidden) loadForecast(); }, 10 * 60 * 1000);
}

setStaticTexts();
wire();
renderAll();
loadForecast();
if ('serviceWorker' in navigator && location.protocol.startsWith('http')) navigator.serviceWorker.register('sw.js').catch(() => {});
