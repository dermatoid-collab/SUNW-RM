# Alba · Tramonto — widget Android minimalista

Widget per la home di Android che mostra, per la posizione corrente:

- **alba** e **tramonto** di oggi;
- **durata del giorno** e variazione rispetto a ieri (es. `−2m 54s`);
- la **curva dell'altezza del Sole** dalla mezzanotte alla mezzanotte, con l'orizzonte e un punto che segna la posizione attuale del Sole (tratto colorato sopra l'orizzonte, attenuato sotto).

Ridimensionando il widget a una sola riga di altezza resta solo la riga con gli orari.

C'è anche una versione **1×1** (voce separata nel selettore dei widget, «Alba · Tramonto 1×1»): mini-curva con il Sole e l'orario del **prossimo evento**, cioè l'alba prima che il Sole sorga, il tramonto durante il giorno, l'alba di domani dopo il tramonto. Qualunque widget ristretto a una colonna passa automaticamente a questo layout.

## Come funziona

- I calcoli usano le equazioni del **NOAA Solar Calculator** (Meeus, *Astronomical Algorithms*), fatte girare sul telefono: nessun accesso a internet.
  Alba e tramonto si riferiscono al bordo superiore del disco con rifrazione standard (−0,833°), e vengono raffinati iterativamente; l'errore è sotto il minuto per latitudini entro ±72°. Notte polare e sole di mezzanotte sono gestiti.
- **Posizione**: di default usa l'ultima posizione nota del dispositivo (permesso *approssimativo*, nessun GPS attivo). In alternativa si inseriscono le coordinate a mano. Senza nessuna posizione usa Parma.
- **Aggiornamento**: ogni 30 minuti (per far avanzare il punto sulla curva) e in più subito dopo alba, tramonto e mezzanotte, con allarmi non esatti che non svegliano il telefono e non richiedono permessi speciali.
- Su Android 12+ i colori seguono la palette Material You dello sfondo.

Toccare il widget apre la schermata delle impostazioni (anche dall'icona dell'app).

## Compilare

Requisiti: JDK 17 e Android SDK (API 35).

```sh
./gradlew testDebugUnitTest   # test del calcolo solare
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Ogni push su GitHub esegue la workflow **Android build**, che lancia i test e allega gli APK (debug e release) come artifact `sunw-apk` scaricabile dalla pagina della run.

## Struttura

| File | Ruolo |
|---|---|
| `SunCalculator.kt` | posizione del Sole, alba/tramonto/mezzogiorno solare |
| `WidgetRenderer.kt` | `RemoteViews` e disegno della curva |
| `SunWidgetProvider.kt` | ciclo di vita del widget e pianificazione degli aggiornamenti |
| `SunWidgetProviderSmall.kt` | voce 1×1 nel selettore dei widget |
| `LocationStore.kt` | posizione automatica o manuale |
| `SettingsActivity.kt` | schermata impostazioni |

minSdk 26 (Android 8.0), nessuna dipendenza esterna.
