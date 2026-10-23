// A believable Open-Meteo answer built around "now", for the tests and for looking at the page without a network.
const pad = (n) => String(n).padStart(2, '0');

function localParts(ms, tz) {
  const f = new Intl.DateTimeFormat('en-US', { timeZone: tz, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
  const p = {};
  for (const { type, value } of f.formatToParts(new Date(ms))) p[type] = value;
  return p;
}
const localText = (ms, tz) => { const p = localParts(ms, tz); return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`; };

export function makeForecast(nowMs = Date.now(), tz = 'Europe/Rome') {
  const hourMs = Math.floor(nowMs / 3600000) * 3600000;
  // From local midnight yesterday: 9 days of hours.
  const today = localParts(nowMs, tz);
  const midnightToday = hourMs - Number(today.hour) * 3600000;
  const start = midnightToday - 24 * 3600000;
  const n = 9 * 24;
  const hourly = { time: [], temperature_2m: [], apparent_temperature: [], precipitation: [], precipitation_probability: [], weather_code: [], wind_speed_10m: [], wind_direction_10m: [], wind_gusts_10m: [], pressure_msl: [], is_day: [] };
  for (let i = 0; i < n; i++) {
    const ms = start + i * 3600000;
    const h = Number(localParts(ms, tz).hour);
    const day = Math.floor(i / 24);
    const t = 13 + 5 * Math.sin(((h - 9) / 24) * 2 * Math.PI) + Math.sin(day * 1.3) * 2;
    const wet = Math.sin(i / 9) > 0.55;
    hourly.time.push(localText(ms, tz));
    hourly.temperature_2m.push(Math.round(t * 10) / 10);
    hourly.apparent_temperature.push(Math.round((t - 2) * 10) / 10);
    hourly.precipitation.push(wet ? Math.round((0.3 + 1.7 * Math.abs(Math.sin(i / 5))) * 10) / 10 : 0);
    hourly.precipitation_probability.push(wet ? 55 + (i % 4) * 10 : 5);
    hourly.weather_code.push(wet ? 61 : h > 10 && h < 16 ? 1 : 3);
    hourly.wind_speed_10m.push(Math.round((6 + 5 * Math.sin(i / 7) + (wet ? 6 : 0)) * 10) / 10);
    hourly.wind_direction_10m.push(Math.round((200 + 80 * Math.sin(i / 20) + 360) % 360));
    hourly.wind_gusts_10m.push(Math.round((12 + 9 * Math.sin(i / 7) + (wet ? 12 : 0)) * 10) / 10);
    hourly.pressure_msl.push(Math.round((1012 + 6 * Math.sin(i / 30)) * 10) / 10);
    hourly.is_day.push(h >= 7 && h < 18 ? 1 : 0);
  }
  const minutely = { time: [], temperature_2m: [] };
  const steps = 96 + 4;
  for (let k = steps; k >= 0; k--) {
    const ms = Math.floor(nowMs / 900000) * 900000 - k * 900000;
    const h = Number(localParts(ms, tz).hour) + Number(localParts(ms, tz).minute) / 60;
    minutely.time.push(localText(ms, tz));
    minutely.temperature_2m.push(Math.round((13 + 5 * Math.sin(((h - 9) / 24) * 2 * Math.PI)) * 10) / 10);
  }
  const daily = { time: [], weather_code: [], temperature_2m_max: [], temperature_2m_min: [], precipitation_sum: [], precipitation_probability_max: [], wind_gusts_10m_max: [], sunrise: [], sunset: [] };
  for (let d = 0; d < 9; d++) {
    const slice = (a) => a.slice(d * 24, d * 24 + 24);
    daily.time.push(hourly.time[d * 24].slice(0, 10));
    daily.weather_code.push(slice(hourly.weather_code)[13]);
    daily.temperature_2m_max.push(Math.max(...slice(hourly.temperature_2m)));
    daily.temperature_2m_min.push(Math.min(...slice(hourly.temperature_2m)));
    daily.precipitation_sum.push(slice(hourly.precipitation).reduce((a, b) => a + b, 0));
    daily.precipitation_probability_max.push(Math.max(...slice(hourly.precipitation_probability)));
    daily.wind_gusts_10m_max.push(Math.max(...slice(hourly.wind_gusts_10m)));
    daily.sunrise.push(`${hourly.time[d * 24].slice(0, 10)}T07:31`);
    daily.sunset.push(`${hourly.time[d * 24].slice(0, 10)}T18:09`);
  }
  return { latitude: 44.8, longitude: 10.33, timezone: tz, utc_offset_seconds: 3600, hourly, minutely_15: minutely, daily };
}

export const geocoding = (name) => ({
  results: /spilla/i.test(name)
    ? [{ name: 'Prato Spilla', latitude: 44.4, longitude: 10.0, admin2: 'Parma', admin1: 'Emilia-Romagna', country: 'Italia' }]
    : [
      { name: 'Livigno', latitude: 46.5386, longitude: 10.1357, admin2: 'Sondrio', admin1: 'Lombardia', country: 'Italia' },
      { name: 'Livigno Alpine', latitude: 46.54, longitude: 10.14, admin1: 'Lombardia', country: 'Italia' },
    ],
});
