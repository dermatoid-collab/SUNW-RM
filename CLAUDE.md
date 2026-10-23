# Istruzioni per Claude

## Git

- **Chiedere sempre il consenso esplicito prima di ogni `git push`.** Si può fare commit in locale, ma il push avviene solo dopo una conferma dell'utente per quel push specifico.
- Non aprire pull request senza una richiesta esplicita.

## Lingua

- Rispondere in italiano.

## Progetto

- App e widget Android «Alba · Tramonto» (Kotlin, solo API di sistema, minSdk 26). Vedi `README.md`.
- Roadmap e stato delle funzioni: `ROADMAP.md`. Prime versioni sviluppate: pagina Sole e Luna per data (`SunMoonActivity`), radar Windy + meteogramma (`MeteogramActivity`), allerte della Protezione Civile (`alerts/`).
- In questo ambiente cloud gli artefatti Google (Android SDK, AGP) non sono scaricabili: la build completa e i test Robolectric girano su GitHub Actions (`.github/workflows/android.yml`). I calcoli di Sole e Luna si possono verificare localmente su JVM.
- Versione web (PWA statica) in `web/`: JavaScript a moduli senza build, test con `node --test web/tests/*.test.mjs`, prova nel browser con dati simulati (`web/tests/e2e.mjs`, Playwright). `web/js/themes.js` si genera con `tools/gen_web_themes.py` da `Appearance.kt`. Niente chiavi nel codice di `web/` (repository pubblico). Pubblicazione su Cloudflare Pages con cartella radice `web`.
