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
- il **meteo** (MeteoBlue): in alto oggi (icona, temperatura, percepita, UV, massima/minima, pioggia, vento), sotto una riga con i giorni successivi (icona, massima/minima, giorno, data). Toccando oggi o un giorno si apre il **dettaglio** in stile Meteoblue: giorni selezionabili, riepilogo, alba/tramonto, Luna, pioggia, vento e tabella ora per ora (ogni ora o ogni 3 ore: la scelta vale per tutti i giorni, anche aprendone un altro, finché non la cambi). I dati sono tenuti in cache e aggiornati al massimo ogni ora; offline si vede l'ultimo aggiornamento. Il **mirino rainSPOT di oggi**, a sinistra di massima e minima, apre la pagina **radar e meteogramma**: in alto la mappa di Windy (radar delle ultime ore o pioggia prevista, con barra delle ore, Play e zoom), sotto il meteogramma dei 7 giorni (temperatura su giorno/notte, pioggia, vento, ora attuale); toccando un giorno si apre il suo dettaglio. Subito sotto la mappa c'è la sezione **webcam** (descritta più avanti), poi la scheda **Ultime 24 ore**: temperatura ogni 15 minuti (valori di Open-Meteo, senza chiave: sono del modello, non di una stazione), con minima, massima, escursione e variazione dell'ultima ora; **tocco lungo e trascina** mostra un cursore con giorno, ora e temperatura (anche sul meteogramma, dove le ore passate sono sbiadite). Sotto, la scheda **Pressione, ultime 24 ore** (mbar, ridotta al livello del mare, ogni ora, da Open-Meteo): stesso stile, con minima, massima, escursione e variazione delle ultime 3 ore; nel meteogramma la pressione è una **linea viola sottile** sopra la temperatura (scala propria) e il suo valore compare nell'etichetta del cursore. Poi la scheda **Vento, ultime 24 ore** (vento medio a 10 m in km/h, ogni ora, da Open-Meteo): stesso stile, linea **azzurra**, con minima, massima, media e variazione dell'ultima ora, e **frecce di direzione** (dove soffia il vento) alla base, una ogni due ore; il cursore mostra velocità e freccia. Nel meteogramma c'è anche una **linea azzurra sottile** col vento medio (scala propria, come la pressione); sotto restano le frecce di direzione ogni 3 ore, senza numeri, e l'etichetta del cursore riporta **temperatura, vento (con freccia) e pressione**. Il meteogramma si apre **dall'inizio del grafico** (l'inizio delle ultime 24 ore, cioè ieri), con l'ora attuale più a destra. La sezione **webcam**: una riga con le tue webcam (ognuna con la sua immagine, se le dai un indirizzo immagine, altrimenti un'icona di videocamera col tipo di pagina; il nome è sotto, come per le vicine), una riga con le anteprime delle quattro più vicine e il pulsante «Tutte le webcam», che apre l'elenco con le schede **Vicine** (anteprime del servizio Windy Webcams, che si ricaricano ogni minuto a webcam aperta) e **Le mie webcam** (pagine o lettori web a tua scelta, aggiungibili, modificabili ed eliminabili, aperti nell'app o nel browser; fanno parte del backup delle impostazioni).

Per le località in Italia, un'**icona d'allerta** ⚠ gialla, arancione o rossa compare accanto alla descrizione del tempo nella tile meteo quando il bollettino della Protezione Civile segnala un'allerta per oggi o domani (rischio idrogeologico, temporali, idraulico); toccandola si vedono zona, rischi e ora del bollettino. Nella pagina del giorno l'allerta è una striscia colorata, con un segno colorato sulla scheda del giorno. Con l'app chiusa, circa ogni ora l'app controlla il bollettino e manda una **notifica** quando la zona della località in uso entra in allerta gialla, arancione o rossa (una per giorno e livello, appena il bollettino è scaricato, dal controllo in background ogni ora o dall'app aperta; si spegne in Impostazioni › Allerte meteo).

Toccando la tile del Sole o della Luna si apre **Sole e Luna per data**, un'unica pagina: dal Sole parte in alto, dalla Luna in fondo. In alto restano fissi la località e la data (toccala per scegliere un giorno entro ±6 mesi); in basso le schede dei giorni e il cursore con «Oggi»; scorre la parte centrale, in tre sezioni. **Sole**: curva con fasce dei crepuscoli e ora blu e d'oro (toccandola si leggono ora, altezza, direzione e ombra), alba e tramonto con azimut, luce utile, crepuscoli, bussola, confronti, i valori **durata del giorno** e **altezza massima del Sole**, e il grafico della durata del giorno nell'anno. **Alba e tramonto**: due curve nell'anno in ora locale, con i cambi dell'ora legale. **Luna**: fase, sorgere e tramonto, i valori **notte**, **Luna in cielo** e **altezza massima della Luna**, e un grafico con selettore tra durata della notte e Luna in cielo (con le lune piene e nuove). Ogni grafico ha la sua legenda e trascinandoci il dito si cambia la data di tutta la pagina; uno swipe a sinistra o a destra passa al giorno dopo o prima: la pagina segue il dito e il giorno vicino le scorre accanto (lo stesso vale per il meteo giornaliero).

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
Sfondi (temi): **SUNW-RM Legacy** (l'aspetto originale, ex «Antracite»), Nero, Vetro (semitrasparente), Material You, Tokyo Night (con la scala «Cielo» ricavata dalla palette Tokyo Night), Catppuccin, Nord, Dracula, Rosé Pine e, come in HRV-RM, Gruvbox Dark, One Dark, Solarized Dark; poi cinque temi scuri dai toni medi e colorati (Everforest, Night Owl, Palenight, Ayu Mirage, Vino) e sei temi chiari: Solarized Light, Catppuccin Latte, Gruvbox Light, Ardesia chiaro, Salvia e Duna. In più otto temi vivaci: MeteoBlue (blu notte), ilMeteo (arancio), Azzurro cielo, Giallo sole, Turchese, Viola elettrico, Rosso corallo e Verde lime. Infine i quattro temi **Minimalist** di ERG-RM, HRV-RM e WIND-RM (Neutro, Seppia, Salvia, Lavanda, ciascuno scuro e chiaro), a bassa saturazione e con la scala «Cielo» quasi incolore. In Impostazioni → Aspetto i temi sono raggruppati per categoria (Sistema, Community scuri e chiari, Originali scuri e chiari, Minimalist), con **SUNW-RM Legacy** sempre per primo e da solo, senza titolo. Nei temi chiari le barre di sistema hanno icone scure e l'accento troppo chiaro (ambra, bianco) viene scurito per restare leggibile. La pagina Impostazioni segue il tema e l'accento scelti, subito dopo la selezione.

**Batteria:** al primo avvio (dopo la richiesta delle notifiche), se il risparmio energetico limita l'app, un avviso spiega perché il widget ne ha bisogno e porta alla scelta di sistema «senza restrizioni»; in Impostazioni → Batteria si vede lo stato e si può riaprire la scelta. Le «App in stop» di Samsung non sono rilevabili e sono solo nominate nel testo.

## Come funziona

- **Luna**: posizione con l'algoritmo di Paul Schlyter (termini periodici principali), illuminazione dall'angolo di fase vero Sole–Luna. Confrontata con PyEphem: illuminazione entro 0,1 %, levata/tramonto entro ~1 minuto (rifrazione standard, bordo superiore), fasi principali entro ~10 minuti.
- I calcoli del Sole usano le equazioni del **NOAA Solar Calculator** (Meeus, *Astronomical Algorithms*), fatte girare sul telefono, senza internet. Solo la ricerca delle località per nome usa il servizio di geocoding del sistema e richiede una connessione (l'app non chiede il permesso Internet; su telefoni senza servizi Google la ricerca può non essere disponibile).
  Alba e tramonto si riferiscono al bordo superiore del disco con rifrazione standard (−0,833°), e vengono raffinati iterativamente; l'errore è sotto il minuto per latitudini entro ±72°. Notte polare e sole di mezzanotte sono gestiti.
- **Posizione**: di default usa l'ultima posizione nota del dispositivo (permesso *approssimativo*, nessun GPS attivo). In alternativa si **cerca una località per nome** (es. «Livigno», «Passo dello Stelvio») con il Geocoder di Android, oppure si inseriscono le coordinate a mano. Senza nessuna posizione usa Parma.
- **Aggiornamento**: un job di sistema ogni 15 minuti (di giorno e di notte, per far avanzare il punto sulla curva), più un allarme esatto con risveglio ad alba, tramonto e mezzanotte (permesso concesso all'installazione), il ridisegno all'accensione dello schermo e quello di sistema ogni 30 minuti. In Impostazioni → Batteria una riga per causa mostra quando e quante volte il widget è stato ridisegnato.
- Su Android 12+ i colori seguono la palette Material You dello sfondo.


## Notifiche meteo

Oltre alle allerte della Protezione Civile (che restano com'erano), l'app avvisa **in anticipo** sul meteo significativo della località in uso. Ogni 30 minuti (job di sistema, anche ad app chiusa) controlla la previsione di Open-Meteo, sia per la località in uso sia per la posizione del dispositivo quando è altrove (ognuna con i suoi eventi e il suo stato; l'intestazione della notifica dice «📍 Parma» per la località impostata e «Dispositivo · Milano» per la posizione del dispositivo), e segue undici eventi: pioggia in arrivo (probabilità ≥70% e ≥0,2 mm/h, dati ogni 15 minuti dove ci sono), pioggia intensa (≥5 mm/h), temporale, **grandine** (codice meteo di grandine, oppure temporale con CAPE ≥1500 J/kg e zero termico sotto i 4200 m; assorbe la notifica del temporale), neve (≥1 cm in 3 ore), vento forte (raffiche ≥50 km/h), caldo (≥35 °C), freddo (≤−5 °C), ghiaccio (≤0 °C con superficie bagnata), nebbia (visibilità ≤200 m) e sbalzo termico (≥8 °C in 12 ore). Ogni evento ha due soglie, **ON** per partire e **OFF** per finire, e tra le due mantiene lo stato: finisce solo dopo due aggiornamenti OFF davvero diversi (almeno 15 minuti l'uno dall'altro e con dati cambiati, oppure 60 minuti). Un evento è un *episodio* (una serie di ore sopra la soglia), riconosciuto tra un aggiornamento e l'altro per sovrapposizione nel tempo e non per «ore da adesso»; si segue da quando supera ON e si notifica quando entra nel suo anticipo (da 1 ora per la pioggia imminente a 24 ore per caldo e freddo). Il peggioramento di livello notifica subito; un evento dello stesso tipo poco dopo una notifica (6 ore) non viene ripetuto se non è più forte; la pioggia intensa assorbe la pioggia in arrivo. Dati mancanti non cambiano lo stato. Lo stato (eventi, contatori, ultima notifica) è salvato su file, per località. Le icone sono quelle dell'app (pioggia, temporale, neve, nebbia…) più tre nuove (vento, termometro, sbalzo). In Impostazioni → **Notifiche meteo** c'è un interruttore generale e uno per ogni evento, e per ognuno il pulsante **Soglie**: si modificano le soglie ON e OFF, il livello forte e l'anticipo (con controllo dell'ordine OFF, ON, forte e dei limiti; «Predefiniti» riporta l'evento ai valori iniziali). **Parametri generali** regola le conferme OFF necessarie (2), il tempo minimo tra due conferme (15 minuti) e la pausa tra notifiche dello stesso tipo (6 ore); **Ripristina tutto** riporta tutto ai valori iniziali.

Il **riepilogo meteo ricorrente** manda ogni giorno, agli orari dell'elenco (04:00, 12:00 e 20:00 all'inizio), una notifica con il meteo delle prossime 12 ore (tempo, temperature, pioggia, raffiche). In Impostazioni → **Riepilogo meteo ricorrente** gli orari si aggiungono (selettore dell'ora) e si eliminano; l'allarme è esatto e si reimposta dopo il riavvio e i cambi di ora.

## Lingua

L'app è in italiano e in inglese e segue la lingua del telefono. Da Android 13 si può scegliere una lingua solo per l'app: Impostazioni → App → Alba · Tramonto → Lingua.

## Meteo

Le previsioni arrivano dal pacchetto MeteoBlue `basic-1h_basic-day`. La chiave è il secret GitHub `METEOBLUE_API_KEY`, inserito nell'APK al momento della build: senza secret la scheda meteo lo segnala. Le webcam vicine usano l'API Windy Webcams (chiave gratuita nel secret GitHub `WINDY_WEBCAMS_API_KEY`, inserita nell'APK come quella di MeteoBlue; senza chiave restano le «Mie webcam»). Le icone sono disegnate dall'app (non sono quelle di MeteoBlue). Il pacchetto non fornisce le ore di sole: al loro posto si mostra l'indice UV.

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
| `SunCalculator.kt` | posizione del Sole (altezza, azimut), alba/tramonto/mezzogiorno, crepuscoli, equinozi e solstizi |
| `webcams/` | webcam: client Windy Webcams, elenco personale, elenco e visualizzatore |
| `weather/PastTemperatures.kt` | temperature di ieri e oggi da Open-Meteo, orarie e ogni 15 minuti (cache 30 minuti); `weather/Last24hView.kt` è il grafico delle ultime 24 ore |
| `weather/MeteogramActivity.kt`, `weather/MeteogramView.kt` | pagina radar (mappa Windy) e meteogramma (7 giorni ora per ora) |
| `alerts/` (`Bulletin.kt`, `AlertZones.kt`, `AlertRepository.kt`, `AlertUi.kt`, `AlertNotifier.kt`, `AlertJobService.kt`) | allerte della Protezione Civile: bollettino CAP, zone di allerta, download, icona e dettaglio, notifiche e controllo orario in background |
| `watch/` (`EventType.kt`, `EventTracker.kt`, `WatchData.kt`, `WeatherWatch.kt`, `WatchJobService.kt`, `WeatherNotifier.kt`, `DigestScheduler.kt`, `DigestReceiver.kt`, `DigestNotifier.kt`) | notifiche sul meteo significativo (11 eventi con isteresi, controllo ogni 30 minuti con Open-Meteo) e riepiloghi meteo ricorrenti (04:00, 12:00 e 20:00 di partenza, modificabili) |
| `SunDayFacts.kt`, `YearData.kt`, `SunMoonActivity.kt`, `SunMoonViews.kt` | pagina «Sole e Luna per data»: dati del giorno e dell'anno, pagina, curva, bussola e grafici annuali |
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
