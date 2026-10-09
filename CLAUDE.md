# Istruzioni per Claude

## Git

- **Chiedere sempre il consenso esplicito prima di ogni `git push`.** Si può fare commit in locale, ma il push avviene solo dopo una conferma dell'utente per quel push specifico.
- Non aprire pull request senza una richiesta esplicita.

## Lingua

- Rispondere in italiano.

## Progetto

- App e widget Android «Alba · Tramonto» (Kotlin, solo API di sistema, minSdk 26). Vedi `README.md`.
- Funzioni concordate ma non ancora sviluppate (allerte, radar): `ROADMAP.md`. Pagina Sole e Luna per data (`SunMoonActivity`) e meteogramma (`MeteogramActivity`) hanno una prima versione.
- In questo ambiente cloud gli artefatti Google (Android SDK, AGP) non sono scaricabili: la build completa e i test Robolectric girano su GitHub Actions (`.github/workflows/android.yml`). I calcoli di Sole e Luna si possono verificare localmente su JVM.
