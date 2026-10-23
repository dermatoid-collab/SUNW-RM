// Time zones without a library: the offset of a zone at an instant, local midnight/noon of a date, formatting.
// Instants are epoch seconds (numbers), as in the Android app's calculators.

const formatters = new Map();

function formatter(tz) {
  let f = formatters.get(tz);
  if (!f) {
    f = new Intl.DateTimeFormat('en-US', {
      timeZone: tz, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
    formatters.set(tz, f);
  }
  return f;
}

/** Local wall-clock parts of [epoch] in [tz]. */
export function parts(epoch, tz) {
  const p = {};
  for (const { type, value } of formatter(tz).formatToParts(new Date(epoch * 1000))) p[type] = Number(value);
  return { year: p.year, month: p.month, day: p.day, hour: p.hour % 24, minute: p.minute, second: p.second };
}

/** Seconds [tz] is ahead of UTC at [epoch]. */
export function offsetSeconds(epoch, tz) {
  const p = parts(epoch, tz);
  return Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second) / 1000 - Math.floor(epoch);
}

/** The instant of [hour]:[minute] on [date] ('YYYY-MM-DD') in [tz]. */
export function at(date, hour, minute, tz) {
  const [y, m, d] = date.split('-').map(Number);
  const wall = Date.UTC(y, m - 1, d, hour, minute) / 1000;
  let t = wall - offsetSeconds(wall, tz);
  t = wall - offsetSeconds(t, tz); // once more: the offset at the answer, across a change of clock
  return t;
}

export const startOfDay = (date, tz) => at(date, 0, 0, tz);
export const noon = (date, tz) => at(date, 12, 0, tz);

/** 'YYYY-MM-DD' of [epoch] in [tz]. */
export function dateOf(epoch, tz) {
  const p = parts(epoch, tz);
  return `${p.year}-${String(p.month).padStart(2, '0')}-${String(p.day).padStart(2, '0')}`;
}

export function addDays(date, days) {
  const [y, m, d] = date.split('-').map(Number);
  const t = new Date(Date.UTC(y, m - 1, d + days));
  return t.toISOString().slice(0, 10);
}

/** 'HH:MM' of [epoch] in [tz]. */
export function hhmm(epoch, tz) {
  const p = parts(epoch, tz);
  return `${String(p.hour).padStart(2, '0')}:${String(p.minute).padStart(2, '0')}`;
}

/** Minutes since local midnight (fractional). */
export function minutesOfDay(epoch, tz) {
  const p = parts(epoch, tz);
  return p.hour * 60 + p.minute + p.second / 60;
}

/** Parses an Open-Meteo local time ('2026-10-23T14:00') as that wall time in [tz]. */
export function fromLocal(text, tz) {
  const [date, time] = text.split('T');
  const [h, mi] = time.split(':').map(Number);
  return at(date, h, mi, tz);
}
