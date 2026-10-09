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

## 2. Allerte meteo — prima versione sviluppata (Italia)

- **Fonte scelta:** bollettino di criticità nazionale della Protezione Civile (dati aperti CC BY 4.0
  su GitHub, `pcm-dpc/DPC-Bollettini-Criticita-Idrogeologica-Idraulica`): ogni giorno entro le 16
  le allerte gialla/arancione/rossa per rischio idrogeologico, temporali e idraulico su 156 zone,
  per oggi e domani. Raggiungibile e verificato anche da qui, a differenza di Meteoalarm.
- **Zona della località:** contorni delle zone inclusi nell'app (`assets/alert_zones.json`, generato
  da `tools/make_alert_zones.py`), ricerca punto-in-poligono senza rete.
- **Dove:** striscia colorata nella tile meteo (oggi, altrimenti domani), striscia nella pagina del
  giorno e segno colorato sulla scheda del giorno; tocco → dettaglio con zona, rischi, ora del
  bollettino e fonte.
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

Implementata in `SunMoonActivity` (cursore ±6 mesi confermato). Da provare sul telefono e rifinire.

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

## Note

- Dall'ambiente cloud di sviluppo questi servizi non sono raggiungibili (proxy): indirizzi
  e condizioni vanno verificati prima di implementare.
