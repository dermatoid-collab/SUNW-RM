// Open-Meteo (no key): hourly forecast with the last day, 15-minute temperatures of the last 24 hours, and the places.
import { fromLocal } from './tz.js';

const HOURLY = 'temperature_2m,apparent_temperature,precipitation,precipitation_probability,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,pressure_msl,is_day';
const DAILY = 'weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,wind_gusts_10m_max,sunrise,sunset';

export function forecastUrl(lat, lon) {
  return 'https://api.open-meteo.com/v1/forecast?' + new URLSearchParams({
    latitude: lat.toFixed(4), longitude: lon.toFixed(4), hourly: HOURLY, daily: DAILY, minutely_15: 'temperature_2m',
    past_days: '1', past_minutely_15: '96', forecast_days: '8', wind_speed_unit: 'kmh', timezone: 'auto',
  });
}

/** Turns an Open-Meteo answer into hours and days with instants as epoch seconds. */
export function parseForecast(json) {
  if (json.error) throw new Error(json.reason || 'Open-Meteo error');
  const tz = json.timezone || 'UTC';
  const h = json.hourly;
  if (!h || !h.time) throw new Error('hourly missing');
  const num = (arr, i) => (arr && arr[i] != null ? arr[i] : null);
  const hours = h.time.map((time, i) => ({
    t: fromLocal(time, tz), local: time,
    temp: num(h.temperature_2m, i), felt: num(h.apparent_temperature, i), rain: num(h.precipitation, i),
    prob: num(h.precipitation_probability, i), code: num(h.weather_code, i),
    wind: num(h.wind_speed_10m, i), dir: num(h.wind_direction_10m, i), gust: num(h.wind_gusts_10m, i),
    pressure: num(h.pressure_msl, i), isDay: h.is_day ? h.is_day[i] === 1 : null,
  }));
  const m = json.minutely_15;
  const fine = m && m.time ? m.time.map((time, i) => ({ t: fromLocal(time, tz), temp: m.temperature_2m[i] })).filter((p) => p.temp != null) : [];
  const d = json.daily;
  const days = d && d.time ? d.time.map((date, i) => ({
    date, code: num(d.weather_code, i), tmax: num(d.temperature_2m_max, i), tmin: num(d.temperature_2m_min, i),
    rain: num(d.precipitation_sum, i), prob: num(d.precipitation_probability_max, i), gust: num(d.wind_gusts_10m_max, i),
    sunrise: d.sunrise && d.sunrise[i] ? fromLocal(d.sunrise[i], tz) : null, sunset: d.sunset && d.sunset[i] ? fromLocal(d.sunset[i], tz) : null,
  })) : [];
  return { tz, hours, fine, days, fetchedAt: Math.floor(Date.now() / 1000) };
}

export async function fetchForecast(lat, lon, fetchImpl = fetch) {
  const res = await fetchImpl(forecastUrl(lat, lon));
  if (!res.ok) throw new Error('HTTP ' + res.status);
  return parseForecast(await res.json());
}

// ---- last 24 hours, from the hours and the 15-minute series

/** The last 24 hours up to [now]: 15-minute temperatures when there are, otherwise hourly. */
export function last24Temperature(f, now) {
  const start = now - 24 * 3600;
  if (f.fine.length) return f.fine.filter((p) => p.t >= start && p.t <= now);
  const end = Math.floor(now / 3600) * 3600;
  return f.hours.filter((p) => p.t >= end - 24 * 3600 && p.t <= end && p.temp != null).map((p) => ({ t: p.t, temp: p.temp }));
}

/** The hourly rows of the last 24 hours up to the current hour, oldest first. */
export function last24Hours(f, now) {
  const end = Math.floor(now / 3600) * 3600;
  return f.hours.filter((p) => p.t >= end - 24 * 3600 && p.t <= end);
}

// ---- weather codes (WMO): a symbol and a short name

const CODES = [
  [[0], '☀️', 'Sereno', 'Clear'], [[1], '🌤️', 'Quasi sereno', 'Mostly clear'], [[2], '⛅', 'Poco nuvoloso', 'Partly cloudy'],
  [[3], '☁️', 'Coperto', 'Overcast'], [[45, 48], '🌫️', 'Nebbia', 'Fog'], [[51, 53, 55, 56, 57], '🌦️', 'Pioggerella', 'Drizzle'],
  [[61, 63, 80, 81], '🌧️', 'Pioggia', 'Rain'], [[65, 66, 67, 82], '🌧️', 'Pioggia forte', 'Heavy rain'],
  [[71, 73, 75, 77, 85, 86], '🌨️', 'Neve', 'Snow'], [[95], '⛈️', 'Temporale', 'Thunderstorm'], [[96, 99], '⛈️', 'Grandine', 'Hail'],
];
export function describeCode(code, lang = 'it') {
  const c = CODES.find((x) => x[0].includes(code));
  return c ? { symbol: c[1], name: lang === 'it' ? c[2] : c[3] } : { symbol: '·', name: '' };
}

// ---- places

export function searchUrl(query, lang) {
  return 'https://geocoding-api.open-meteo.com/v1/search?' + new URLSearchParams({ name: query, count: '8', language: lang, format: 'json' });
}

/** Results of the Open-Meteo geocoding: { name, lat, lon, detail } with the place's own name (not the municipality's). */
export function parseSearch(json) {
  return (json.results || []).filter((r) => Number.isFinite(r.latitude) && Number.isFinite(r.longitude)).map((r) => ({
    name: r.name, lat: r.latitude, lon: r.longitude,
    detail: [r.name, r.admin3, r.admin2, r.admin1, r.country].filter((x, i, a) => x && a.indexOf(x) === i).join(', '),
  }));
}

export async function searchPlaces(query, lang, fetchImpl = fetch) {
  const res = await fetchImpl(searchUrl(query, lang));
  if (!res.ok) throw new Error('HTTP ' + res.status);
  return parseSearch(await res.json());
}

const plain = (s) => s.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase().trim();

/** Whether a found place is what [query] names: the names contain each other, ignoring case and accents. */
export function matches(query, result) {
  const wanted = plain(query);
  if (!wanted) return true;
  const have = plain(result.name);
  return have.includes(wanted) || wanted.includes(have);
}
