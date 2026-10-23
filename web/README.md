# Alba · Tramonto — versione web

Una PWA (sito installabile sul telefono) con le stesse cose dell'app Android che hanno senso in un browser. Nessuna
compilazione e nessuna dipendenza: file statici (HTML, CSS, JavaScript a moduli) che si servono così come sono.

## Cosa c'è

- **Sole e Luna** per data (‹ oggi ›): curva dell'altezza del Sole con i quattro stili di colore, alba, tramonto, mezzogiorno
  solare, durata, crepuscoli, ora blu e dorata; fase, illuminazione, sorgere e tramonto della Luna e le prossime fasi.
  I calcoli sono il porting di `SunCalculator.kt` e `MoonCalculator.kt`, verificati con gli stessi valori di riferimento
  (NOAA e PyEphem) dei test dell'app.
- **Meteo** da Open-Meteo (senza chiave): adesso, i giorni, la tabella ora per ora (ogni ora / ogni 3 ore, la scelta resta).
- **Radar** di Windy incorporato.
- **Tre grafici delle ultime 24 ore** (temperatura; pressione con gli istogrammi della pioggia; vento con raffica tratteggiata
  e frecce di direzione) e il **meteogramma** di 8 giorni, ognuno con il suo campo dei valori sotto il grafico.
- **Luoghi**: Cerca (geocoding di Open-Meteo, con Recenti e «Usa e basta» / «Aggiungi ai preferiti»), posizione del
  dispositivo e preferiti.
- **Temi**: gli stessi 39 dell'app (`js/themes.js` si genera da `Appearance.kt`), accento e stile della curva.
- **Backup**: esporta e importa lo stesso file JSON dell'app (versione 2): ciò che il web non usa (notifiche meteo, allerte,
  webcam) viene conservato e restituito all'esportazione.

Non ci sono: widget, notifiche in background, allerte della Protezione Civile, webcam (richiedono una chiave o un server).

## Provarla

```sh
cd web && python3 -m http.server 8080      # poi http://localhost:8080
node --test web/tests/*.test.mjs            # test (calcoli, backup, parsing)
NODE_PATH=$(npm root -g) node web/tests/e2e.mjs [cartella-schermate]   # browser vero con dati simulati (serve Playwright)
python3 tools/gen_web_themes.py             # rigenera js/themes.js dopo aver cambiato un tema in Appearance.kt
```

Dall'ambiente cloud di sviluppo Open-Meteo non è raggiungibile: il test nel browser risponde con dati simulati
(`tests/fixtures.mjs`), quindi la prova vera con i dati reali si fa dal telefono o dal PC.

## Pubblicarla su Cloudflare Pages

1. Cloudflare → Workers & Pages → Create → Pages → collega questo repository.
2. **Root directory:** `web` · **Build command:** (vuoto) · **Build output directory:** `/` (o vuoto).
3. In *Build watch paths* inserisci `web/*`, così una modifica all'app Android non ricostruisce il sito.
4. Assegna un sottodominio (per esempio `meteo.tuodominio.it`) e dal sito principale metti un semplice collegamento.
5. Per renderlo privato, proteggi il sottodominio (e le anteprime `*.pages.dev`) con **Cloudflare Access**.

Le chiavi (Windy Webcams, MeteoBlue) non vanno mai nel codice di `web/`, che è pubblico: se un giorno servono, si mettono
come variabili segrete del progetto Pages e si usano da una funzione in `web/functions/`.
