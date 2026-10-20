# Roadmap — funzioni in attesa

Idee concordate ma non ancora sviluppate, ispirate all'app Meteoblue. Ordine proposto:
meteogramma → allerte → radar (prime versioni sviluppate). Ogni funzione in un commit separato, da provare sul telefono.

## 1. Meteogramma 7 giorni — prima versione sviluppata

Implementato in `MeteogramActivity`/`MeteogramView`: si apre toccando il mirino rainSPOT di oggi
nella tile meteo della pagina principale (a sinistra delle pillole max/min).

- **Dati:** già presenti nel pacchetto MeteoBlue `basic-1h` che scarichiamo ogni ora
  (temperatura, precipitazione, vento ora per ora). Nessun servizio nuovo.
- **Disegno:** nostro, nello stile dell'app (non l'immagine Meteoblue, che richiede un
  pacchetto a pagamento e ha sfondo bianco): curva della temperatura con fasce giorno/notte,
  barre della pioggia, velocità e frecce del vento, linea dell'ora attuale.
- **Dove:** pulsante sulla card meteo → pagina dedicata; tocco su un giorno → pagina del giorno.
- **Andamento della giornata** (pagina del giorno, sopra le righe orarie): striscia a passi di 3 ore (ora, icona,
  temperatura, pioggia, vento medio, raffica, direzione) sopra un grafico orario di temperatura e pioggia con le stesse
  colonne; tocco lungo e trascinamento leggono i valori dell'ora. In alto a destra un pallino con la percentuale di
  prevedibilità di MeteoBlue (verde da 80 %, giallo da 60 %, rosso sotto; assente se il dato manca). Le raffiche orarie
  vengono da Open-Meteo (`wind_gusts_10m`), non da MeteoBlue: senza di esse la riga delle raffiche non compare.
  Per oggi le ore già trascorse prendono da Open-Meteo anche temperatura, percepita, pioggia, vento, direzione e icona
  (codici WMO), come nel meteogramma; probabilità di pioggia e rainSPOT restano di MeteoBlue, come tutto dall'ora attuale
  in poi. Le ore trascorse sono attenuate (card e righe orarie) e c'è una linea «adesso» nel grafico.
  Lo stesso vale per il meteogramma: le ultime 24 ore e le ore di oggi già trascorse hanno icone, pioggia e vento di
  Open-Meteo (attenuati), non solo la temperatura; la richiesta a Open-Meteo include ieri (`past_days=1`).

## 2. Allerte meteo — prima versione sviluppata (Italia)

- **Fonte scelta:** bollettino di criticità nazionale della Protezione Civile (dati aperti CC BY 4.0
  su GitHub, `pcm-dpc/DPC-Bollettini-Criticita-Idrogeologica-Idraulica`): ogni giorno entro le 16
  le allerte gialla/arancione/rossa per rischio idrogeologico, temporali e idraulico su 156 zone,
  per oggi e domani. Raggiungibile e verificato anche da qui, a differenza di Meteoalarm.
- **Zona della località:** contorni delle zone inclusi nell'app (`assets/alert_zones.json`, generato
  da `tools/make_alert_zones.py`), ricerca punto-in-poligono senza rete.
- **Dove:** icona ⚠ colorata accanto alla descrizione del tempo nella tile meteo (oggi, altrimenti
  domani; assente senza allerte), striscia nella pagina del giorno e segno colorato sulla scheda; tocco → dettaglio con zona, rischi, ora del
  bollettino e fonte.
- **Notifiche:** controllo orario in background (JobScheduler) e notifica per allerta gialla,
  arancione o rossa nella zona della località in uso, una per giorno e livello; interruttore in
  Impostazioni › Allerte meteo, incluso nel backup.
- **Da fare in seguito:** Meteoalarm (EUMETNET) per le località fuori Italia (feed da verificare);
  bollettino di vigilanza meteorologica (vento, neve…, repo `DPC-Bollettini-Vigilanza-Meteorologica`).

## 3. Radar — prima versione con Windy

Scelta dell'utente: mappa incorporata di Windy (embed in WebView) nella pagina del meteogramma,
con i pulsanti «Radar» (ultime ore) e «Pioggia prevista» (ECMWF); barra delle ore, Play e zoom
sono quelli di Windy. Da verificare sul telefono (da qui windy.com non è raggiungibile).

- **Preferenza dell'utente:** lo stile grafico del radar di **windy.com**.
- **Opzioni da verificare (condizioni d'uso e costi):**
  - Windy: widget incorporabile (embed, livello radar) in una WebView, oppure la Map
    Forecast API (chiave; la versione di prova potrebbe avere limiti, la produzione è a
    pagamento).
  - Alternative gratuite: RainViewer (radar mondiale, ultime ~2 h + previsione 30 min;
    condizioni del servizio gratuito cambiate di recente) o radar della Protezione Civile
    (solo Italia, dati aperti), con mappa di base OpenStreetMap.
- **Dove:** nella stessa pagina del meteogramma (mirino di oggi → radar + meteogramma), sopra il
  meteogramma.

## 4. Pagina «Sole e Luna per data» — prima versione sviluppata

Implementata in `SunMoonActivity` (cursore ±6 mesi confermato), poi riorganizzata come da proposta definitiva: una pagina con tre sezioni (Sole, Alba e tramonto, Luna), barra fissa in alto e schede+cursore fissi in basso, ingresso in alto dal Sole e in fondo dalla Luna, valori espliciti e grafici annuali con legenda e selettore per la Luna. Da provare sul telefono e rifinire.

- **Prototipo:** artifact «Alba · Tramonto — schermate», tavola «Sole e Luna per data (proposta)»
  (https://claude.ai/artifact/KJjPBhHAyGoU2EYyyfjg7L), approvato dall'utente.
- **Apertura:** tocco sulla tile del sole o della luna nella pagina principale.
- **Barra in alto:** freccia indietro + data scelta per esteso; sotto, località e distanza da
  oggi ("today", "in 12 days", "30 days ago").
- **Tile del sole** per la data scelta: curva colorata per elevazione con fasce dei crepuscoli
  sotto l'orizzonte e barrette di ora blu/d'oro; alba e tramonto con azimut (es. "101° ESE");
  durata e differenza col giorno prima; "luce utile" (alba–tramonto civile); tabella dei
  crepuscoli civile/nautico/astronomico + mezzogiorno con altezza massima; bussola con le
  direzioni di alba e tramonto; ora blu e ora d'oro di mattina e sera; confronto con oggi e
  con i solstizi. Curva interattiva: toccando/trascinando mostra ora, altezza, direzione e
  lunghezza dell'ombra; per oggi il cursore parte da "adesso".
- **Striscia dell'anno** (tra la luna e il selettore): durata del giorno lungo l'intervallo del
  cursore, segni di solstizi/equinozi, punto sulla data scelta, prossimo evento
  ("Winter solstice 21/12 · in 73 days · −1h 25m of daylight").
- **Tile della luna** per la data scelta: fase disegnata, %, nome, sorge/tramonta, prossime fasi.
- **Sotto le tile:** schede dei giorni nello stile della pagina meteo (7 giorni centrati sulla
  data scelta, puntino su oggi) e **cursore** per scorrere la data dinamicamente (±6 mesi,
  ampiezza da confermare), con pulsante "Today".
- **Calcoli:** quelli già verificati dell'app (SunCalculator, MoonCalculator), non le formule
  semplificate del prototipo.

## 5. Avviso sulla batteria — sviluppato

- **Perché:** il risparmio energetico di One UI (e le «App in stop») può rimandare l'allarme
  del widget e fermarne gli aggiornamenti.
- **Rilevare:** `PowerManager.isIgnoringBatteryOptimizations` e `ActivityManager.isBackgroundRestricted`;
  le «App in stop» di Samsung non sono rilevabili, quindi il testo le nomina.
- **Avviso una tantum** dopo la richiesta delle notifiche al primo avvio, con «Impostazioni» e «Non ora»;
  la richiesta di sistema (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, permesso nel manifest) o, se non
  disponibile, la pagina batteria dell'app.
- **Riga fissa nelle Impostazioni** con lo stato (limitata o libera) che apre la stessa richiesta.
- **Niente banner permanente** sulla pagina principale.

## 6. Notifiche meteo significative e riepiloghi ricorrenti — prima versione sviluppata

Vedi il README (sezione «Notifiche meteo»). Fonte unica Open-Meteo, località in uso più posizione del dispositivo quando è altrove, nessuna ora di silenzio. Grandine: la copertura dei codici 96/99 e i nomi delle variabili `cape` e `freezing_level_height` vanno controllati alla prima esecuzione.
Soglie ON/OFF, anticipi e livelli forti (pioggia 10 mm/h, raffiche 70 km/h, caldo 38 °C, freddo −10 °C, neve 5 cm, nebbia 100 m,
sbalzo 12 °C) sono valori iniziali da calibrare. Da verificare sul telefono: il job ogni 30 minuti con le restrizioni della batteria,
il numero di notifiche nei giorni di tempo instabile, la precisione della pioggia imminente (dati ogni 15 minuti solo dove
Open-Meteo li ha), la «superficie bagnata» del ghiaccio (umidità ≥90% o pioggia nelle ultime 3 ore) e la nebbia (la
visibilità del modello scende raramente sotto i 200 m). Le soglie sono modificabili in Impostazioni → Notifiche meteo → Soglie (parametri in `watch/Thresholds.kt`). Non fatto: ore di silenzio, località preferite oltre a quelle due,
inclusione nel backup delle impostazioni.

## Altre cose in sospeso

- **Icone orarie:** la mappatura dai codici icona di MeteoBlue alle nostre categorie (`Condition.kt`) è
  approssimata (nel confronto di domenica le icone differivano in 6 ore su 9): serve vedere il codice grezzo.
- **Vento:** l'app mostra solo il minimo del vento medio; MeteoBlue mostra anche il massimo (intervallo).
- **Grafico della Luna** (minuti sopra l'orizzonte): la curva ha gradini reali dovuti alla finestra
  00:00–24:00; un'eventuale media mobile su 3 giorni è da decidere.
- **RainSPOT:** verificare l'allineamento su altre giornate dopo la rimozione dello spostamento a est.
- **Pallino di affidabilità:** da controllare nell'APK se MeteoBlue manda davvero il dato di prevedibilità.
- **Widget:** risolto con il job ogni 15 minuti (misurato: 46 esecuzioni in 11 ore e un quarto, nessun buco, anche di notte).
  L'allarme con risveglio ogni 15 minuti dava poco (5 esecuzioni in 11 ore: Android lo rimanda a telefono fermo) ed è stato
  tolto: restano solo gli allarmi esatti ad alba, tramonto e mezzanotte, l'accensione dello schermo (finché il processo è vivo)
  e l'aggiornamento di sistema ogni 30 minuti (misurato: solo all'installazione). In Impostazioni → Batteria una riga per causa
  mostra l'ultimo ridisegno e il numero; da tenere per ora (costa una scrittura di preferenze a ridisegno, trascurabile).
  Resta l'opzione di un servizio in primo piano (notifica fissa) solo se il job smettesse di bastare: da proporre su richiesta.
- **Swipe tra i giorni (Sole e Luna, meteo giornaliero):** sviluppato come un pager (`DayPager.kt`): la pagina
  segue il dito, il giorno vicino è già disegnato come immagine e le scorre accanto, quindi non resta uno spazio
  vuoto. La direzione si decide durante il gesto (orizzontale: lo scroll verticale viene annullato; verticale: niente
  swipe). Fuori dal gesto (schede dei giorni, slider, «Oggi») resta la dissolvenza incrociata di `DaySlide.kt`, con lo
  stesso movimento delle transizioni tra pagine. Da verificare sul telefono: fluidità, qualche istante senza il vicino
  subito dopo un cambio (le immagini vengono preparate in secondo piano), scatti nel meteo giornaliero.
- **Pressione:** scheda «Pressione, ultime 24 ore» e linea viola sottile nel meteogramma (Open-Meteo, `pressure_msl`,
  mbar = hPa, oraria; è un valore del modello ridotto al livello del mare, non di una stazione). Da verificare sul
  telefono: leggibilità della linea sopra la temperatura (scala propria, almeno 8 mbar di altezza) e confronto con la
  pressione di una stazione vicina. MeteoBlue non è stato usato perché il pacchetto basic non la include.
- **Meteoalarm** per le località fuori Italia (già indicato nella sezione 2).

## Note

- Dall'ambiente cloud di sviluppo questi servizi non sono raggiungibili (proxy): indirizzi
  e condizioni vanno verificati prima di implementare.
