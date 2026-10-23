// Colours as [r, g, b, a] (0..255, a 0..1): parsing '#RRGGBB' / 'rgba(r,g,b,a)', blending and the temperature scale.

export function parse(c) {
  if (Array.isArray(c)) return c;
  if (c.startsWith('#')) return [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16), 1];
  const m = c.match(/rgba?\(([^)]+)\)/);
  const [r, g, b, a = 1] = m[1].split(',').map(Number);
  return [r, g, b, a];
}

export const css = (c) => {
  const [r, g, b, a] = parse(c);
  return a === 1 ? `rgb(${Math.round(r)},${Math.round(g)},${Math.round(b)})` : `rgba(${Math.round(r)},${Math.round(g)},${Math.round(b)},${a})`;
};

export const withAlpha = (c, alpha) => { const [r, g, b] = parse(c); return css([r, g, b, alpha]); };

/** [from] towards [to] by [t] (0..1), opaque. */
export function blend(from, to, t) {
  const a = parse(from);
  const b = parse(to);
  return css([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t, 1]);
}

export const luminance = (c) => { const [r, g, b] = parse(c); return (0.299 * r + 0.587 * g + 0.114 * b) / 255; };

/** An accent too light for a light page is darkened (amber, white), as in the Android app. */
export const readable = (accent, onLight) => (onLight && luminance(accent) > 0.45 ? blend(accent, '#000000', 0.45) : accent);

const TEMPERATURE_STOPS = [[0, '#7FB2FF'], [8, '#8FDCD0'], [14, '#A6E39A'], [19, '#E4F39A'], [24, '#FFE27A'], [29, '#FFB36B'], [34, '#FF7B6B']];

/** Blue (cold) → green → yellow → red (hot), the colour of the temperature pills and curves. */
export function temperatureColor(celsius) {
  if (celsius <= TEMPERATURE_STOPS[0][0]) return TEMPERATURE_STOPS[0][1];
  for (let i = 0; i < TEMPERATURE_STOPS.length - 1; i++) {
    const [a, ca] = TEMPERATURE_STOPS[i];
    const [b, cb] = TEMPERATURE_STOPS[i + 1];
    if (celsius <= b) return blend(ca, cb, (celsius - a) / (b - a));
  }
  return TEMPERATURE_STOPS[TEMPERATURE_STOPS.length - 1][1];
}

export const PRESSURE_LINE = '#B894FF';
export const WIND_LINE = '#4FC3F7';
export const RAIN_COLOR = '#5B9BE8';
export const MAX_PILL = '#FFE27A';
export const MIN_PILL = '#9FD4FF';

/** Calm winds dim, fresh ones in the text colour, strong ones orange, gales red. */
export function windColor(kmh, p) {
  if (kmh >= 60) return '#FF6B6B';
  if (kmh >= 35) return '#FFB36B';
  if (kmh >= 15) return p.text;
  return p.textSecondary;
}

/** The sky ramp of a theme at an elevation: night, twilights, golden hour, high Sun. */
export function skyColor(p, stops, elevation) {
  if (elevation <= stops[0]) return p.sky[0];
  for (let i = 0; i < stops.length - 1; i++) {
    if (elevation <= stops[i + 1]) return blend(p.sky[i], p.sky[i + 1], (elevation - stops[i]) / (stops[i + 1] - stops[i]));
  }
  return p.sky[p.sky.length - 1];
}
