// "Export / Import settings": the same JSON file as the Android app (version 2), so a backup made on one opens on
// the other. A file of version 1 still imports; what a file lacks keeps its current value.
import { ACCENTS, CURVES } from './store.js';
import { THEMES } from './themes.js';

const APP = 'it.sunw.widget';
const VERSION = 2;

export function exportBackup(store) {
  const place = store.place;
  const keep = store.passthrough;
  return JSON.stringify({
    app: APP,
    version: VERSION,
    place: { automatic: !!place.automatic, lat: place.lat, lon: place.lon, name: place.name ?? null },
    favorites: store.favorites.map((f) => ({ name: f.name, lat: f.lat, lon: f.lon })),
    appearance: { curve: store.curve, accent: store.accent, theme: store.theme },
    alerts: keep.alerts ?? { notify: true },
    watch: keep.watch ?? undefined,
    digest: keep.digest ?? undefined,
    weatherDay: { stepHours: store.stepHours },
    recents: store.recents.map((r) => ({ name: r.name, lat: r.lat, lon: r.lon })),
    webcams: keep.webcams ?? [],
  }, null, 2);
}

/** Restores what [text] contains; throws when it is not a settings file of this app. */
export function importBackup(store, text) {
  const root = JSON.parse(text);
  if (root.app !== APP) throw new Error('Non è un file di impostazioni di Alba · Tramonto');

  const p = root.place;
  if (p && Number.isFinite(p.lat) && Number.isFinite(p.lon)) {
    store.place = { lat: p.lat, lon: p.lon, name: p.name ?? null, automatic: !!p.automatic };
  }
  if (Array.isArray(root.favorites)) {
    for (const f of root.favorites) {
      if (f && typeof f.name === 'string' && Number.isFinite(f.lat) && Number.isFinite(f.lon)) store.addFavorite({ name: f.name, lat: f.lat, lon: f.lon });
    }
  }
  const a = root.appearance;
  if (a) {
    if (CURVES.includes(a.curve)) store.curve = a.curve;
    if (ACCENTS.some((x) => x[0] === a.accent)) store.accent = a.accent;
    if (THEMES.some((t) => t.key === a.theme)) store.theme = a.theme;
  }
  if (root.weatherDay) store.stepHours = root.weatherDay.stepHours === 3 ? 3 : 1;
  if (Array.isArray(root.recents)) {
    store.recents = root.recents.filter((r) => r && typeof r.name === 'string' && r.name && Number.isFinite(r.lat) && Number.isFinite(r.lon))
      .map((r) => ({ name: r.name, lat: r.lat, lon: r.lon }));
  }
  const keep = { ...store.passthrough };
  for (const key of ['alerts', 'watch', 'digest', 'webcams']) if (root[key] !== undefined) keep[key] = root[key];
  store.passthrough = keep;
}
