# Alba · Tramonto — widget Android minimalista

Widget per la home di Android che mostra, per la posizione corrente:

- **alba** e **tramonto** di oggi;
- **durata del giorno** e variazione rispetto a ieri (es. `−2m 54s`);
- la **curva dell'altezza del Sole** dalla mezzanotte alla mezzanotte, con l'orizzonte e un punto che segna la posizione attuale del Sole (tratto colorato sopra l'orizzonte, attenuato sotto).

Ridimensionando il widget a una sola riga di altezza resta solo la riga con gli orari.

Ci sono anche due versioni **1×1**, voci separate nel selettore dei widget. Entrambe mostrano una mini-curva con il Sole e, sotto, **alba e tramonto**: il prossimo evento è in evidenza e l'altro attenuato; dopo il tramonto compaiono gli orari di domani.

- **«Alba · Tramonto 1×1 · grande»**: numeri il più grandi possibile (carattere condensed), curva sottile, angoli squadrati (8dp).
- **«Alba · Tramonto 1×1 · curva»**: curva più alta, numeri un po' più piccoli, angoli arrotondati di sistema.

Qualunque widget ristretto a una colonna passa automaticamente allo stile «grande».

## L'app

Toccando un widget (o dall'icona) si apre la **pagina principale**:

- in alto il nome della località: toccandolo si passa al volo tra i **preferiti** o alla posizione del dispositivo;
- il **widget 4×2** completo, identico a quello sulla home;
- la **Luna**: disegno della fase, percentuale illuminata, nome della fase, levata e tramonto lunare, date di luna nuova, primo quarto e piena, e una riga con i prossimi 7 giorni;
- il **meteo** (MeteoBlue): in alto oggi (icona, temperatura, percepita, UV, massima/minima, pioggia, vento), sotto i giorni successivi con icona di giorno e di notte, temperature, vento, pioggia e UV. I dati sono tenuti in cache e aggiornati al massimo ogni ora; offline si vede l'ultimo aggiornamento.

L'icona ⚙ apre le **impostazioni**:

- **Posizione**: dispositivo, ricerca per nome o coordinate a mano;
- **Preferiti**: «+ Salva la posizione attuale»; tocca per usarne uno, tieni premuto per rimuoverlo;
- **Aspetto**: colore della curva, colore d'accento e sfondo (anche **Tokyo Night**).

### Colore della curva

- **Cielo** (predefinito): il colore segue l'altezza del Sole — notte blu, crepuscolo astronomico/nautico indaco e viola, civile rosa, orizzonte arancio, ora dorata, giallo chiaro col Sole alto. Anche il punto cambia colore.
- **Accento**: colore d'accento sopra l'orizzonte, grigio sotto.
- **Ora dorata e ora blu**: evidenzia solo −6°…−4° (ora blu) e −4°…+6° (ora dorata).
- **Crepuscoli**: curva come «Accento» più tre fasce sotto l'orizzonte (civile 0…−6°, nautico −6…−12°, astronomico −12…−18°).

Colori d'accento: Ambra, Material You, Ghiaccio, Alpino, Corallo, Bianco, Tokyo giallo, Tokyo blu.
Sfondi: Antracite, Nero, Vetro (semitrasparente), Material You, Tokyo Night (con la scala «Cielo» ricavata dalla palette Tokyo Night).

## Come funziona

- **Luna**: posizione con l'algoritmo di Paul Schlyter (termini periodici principali), illuminazione dall'angolo di fase vero Sole–Luna. Confrontata con PyEphem: illuminazione entro 0,1 %, levata/tramonto entro ~1 minuto (rifrazione standard, bordo superiore), fasi principali entro ~10 minuti.
- I calcoli del Sole usano le equazioni del **NOAA Solar Calculator** (Meeus, *Astronomical Algorithms*), fatte girare sul telefono, senza internet. Solo la ricerca delle località per nome usa il servizio di geocoding del sistema e richiede una connessione (l'app non chiede il permesso Internet; su telefoni senza servizi Google la ricerca può non essere disponibile).
  Alba e tramonto si riferiscono al bordo superiore del disco con rifrazione standard (−0,833°), e vengono raffinati iterativamente; l'errore è sotto il minuto per latitudini entro ±72°. Notte polare e sole di mezzanotte sono gestiti.
- **Posizione**: di default usa l'ultima posizione nota del dispositivo (permesso *approssimativo*, nessun GPS attivo). In alternativa si **cerca una località per nome** (es. «Livigno», «Passo dello Stelvio») con il Geocoder di Android, oppure si inseriscono le coordinate a mano. Senza nessuna posizione usa Parma.
- **Aggiornamento**: ogni 30 minuti (per far avanzare il punto sulla curva) e in più subito dopo alba, tramonto e mezzanotte, con allarmi non esatti che non svegliano il telefono e non richiedono permessi speciali.
- Su Android 12+ i colori seguono la palette Material You dello sfondo.


## Lingua

L'app è in italiano e in inglese e segue la lingua del telefono. Da Android 13 si può scegliere una lingua solo per l'app: Impostazioni → App → Alba · Tramonto → Lingua.

## Meteo

Le previsioni arrivano dal pacchetto MeteoBlue `basic-1h_basic-day`. La chiave è il secret GitHub `METEOBLUE_API_KEY`, inserito nell'APK al momento della build: senza secret la scheda meteo lo segnala. Le icone sono disegnate dall'app (non sono quelle di MeteoBlue). Il pacchetto non fornisce le ore di sole: al loro posto si mostra l'indice UV.

## Aggiornamenti senza perdere le impostazioni

Perché un nuovo APK si installi sopra il precedente (mantenendo località, preferiti e aspetto) serve sempre la stessa chiave di firma. La workflow la legge da due secret del repository:

- `SIGNING_KEYSTORE_BASE64`: il keystore `.jks` codificato in base64;
- `SIGNING_PASSWORD`: la password del keystore e della chiave (alias `sunw`).

Senza questi secret gli APK sono firmati con una chiave di debug diversa a ogni build e Android li rifiuta come aggiornamento. In ogni caso, in Impostazioni → Backup si possono esportare e reimportare le impostazioni in un file.

## Compilare

Requisiti: JDK 17 e Android SDK (API 35).

```sh
./gradlew testDebugUnitTest   # test del calcolo solare e lunare
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Ogni push su GitHub esegue la workflow **Android build**, che lancia i test e allega gli APK (debug e release) come artifact `sunw-apk` scaricabile dalla pagina della run.

## Struttura

| File | Ruolo |
|---|---|
| `SunCalculator.kt` | posizione del Sole, alba/tramonto/mezzogiorno solare |
| `WidgetRenderer.kt` | `RemoteViews` e disegno della curva |
| `SunWidgetProvider.kt` | ciclo di vita del widget e pianificazione degli aggiornamenti |
| `SunWidgetProviderSmall.kt`, `SunWidgetProviderSmallCurve.kt` | le due voci 1×1 nel selettore dei widget |
| `LocationStore.kt` | posizione automatica o manuale |
| `MainActivity.kt` | pagina principale: widget, Luna, cambio località |
| `SettingsActivity.kt` | impostazioni: posizione, preferiti, aspetto |
| `MoonCalculator.kt`, `MoonRenderer.kt` | fase, illuminazione, levata/tramonto lunare; disegno del disco |
| `Appearance.kt` | stili della curva, accenti, temi e palette |
| `FavoritesStore.kt` | località preferite |

minSdk 26 (Android 8.0), nessuna dipendenza esterna.
