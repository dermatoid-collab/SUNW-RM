// The user's settings, kept in this browser (localStorage): place, favourites, recents, appearance.
// What only the Android app uses (weather notifications, the Civil Protection switch, own webcams) is kept as it
// came in a backup, so that exporting again gives it back.

export const DEFAULT_PLACE = { lat: 44.8015, lon: 10.3279, name: 'Parma', automatic: false };
export const ACCENTS = [
  ['amber', 'Ambra', '#FFB547'], ['ice', 'Ghiaccio', '#8FD3FF'], ['alpine', 'Alpino', '#8FD48A'], ['coral', 'Corallo', '#FF8A7A'],
  ['white', 'Bianco', '#EDEDED'], ['tokyo_yellow', 'Tokyo giallo', '#E0AF68'], ['tokyo_blue', 'Tokyo blu', '#7AA2F7'],
  ['catppuccin_mauve', 'Catppuccin malva', '#CBA6F7'], ['nord_frost', 'Nord gelo', '#88C0D0'], ['dracula_pink', 'Dracula rosa', '#FF79C6'],
  ['rose_pine', 'Rosé Pine', '#EBBCBA'], ['peach_fuzz', 'Peach Fuzz', '#FFBE98'],
];
export const CURVES = ['sky', 'accent', 'golden', 'twilight'];
export const MAX_RECENTS = 10;

const memory = new Map(); // when localStorage is not available (private window): the page still works

export class Store {
  constructor(storage = globalThis.localStorage) { this.storage = storage; }

  read(key, fallback) {
    try {
      const raw = this.storage?.getItem('sunw.' + key) ?? memory.get(key) ?? null;
      return raw == null ? fallback : JSON.parse(raw);
    } catch { return fallback; }
  }

  write(key, value) {
    const raw = JSON.stringify(value);
    try { this.storage.setItem('sunw.' + key, raw); } catch { memory.set(key, raw); }
  }

  get place() { return this.read('place', DEFAULT_PLACE); }
  set place(p) { this.write('place', p); }

  get favorites() { return this.read('favorites', []); }
  set favorites(list) { this.write('favorites', list); }
  addFavorite(f) { this.favorites = [...this.favorites.filter((x) => x.name !== f.name), f]; }
  removeFavorite(f) { this.favorites = this.favorites.filter((x) => !(x.name === f.name && x.lat === f.lat && x.lon === f.lon)); }

  get recents() { return this.read('recents', []); }
  set recents(list) { this.write('recents', list.slice(0, MAX_RECENTS)); }
  /** Puts [place] first; the same name or the same spot (about 100 m) moves up instead of repeating. */
  addRecent(place) {
    const same = (r) => r.name === place.name || (Math.abs(r.lat - place.lat) < 1e-3 && Math.abs(r.lon - place.lon) < 1e-3);
    this.recents = [place, ...this.recents.filter((r) => !same(r))];
  }

  get theme() { return this.read('theme', 'anthracite'); }
  set theme(key) { this.write('theme', key); }
  get accent() { return this.read('accent', 'amber'); }
  set accent(key) { this.write('accent', key); }
  get curve() { return this.read('curve', 'sky'); }
  set curve(key) { this.write('curve', key); }

  /** The every-hour (1) / every-3-hours (3) choice of the hourly table. */
  get stepHours() { return this.read('stepHours', 1) === 3 ? 3 : 1; }
  set stepHours(h) { this.write('stepHours', h === 3 ? 3 : 1); }

  /** Sections of a backup this version has no use for, kept to give them back on export. */
  get passthrough() { return this.read('passthrough', {}); }
  set passthrough(o) { this.write('passthrough', o); }
}
