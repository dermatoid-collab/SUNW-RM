# Roadmap — funzioni in attesa

Idee concordate ma non ancora sviluppate, ispirate all'app Meteoblue. Ordine proposto:
meteogramma → allerte → radar. Ogni funzione in un commit separato, da provare sul telefono.

## 1. Meteogramma 7 giorni

- **Dati:** già presenti nel pacchetto MeteoBlue `basic-1h` che scarichiamo ogni ora
  (temperatura, precipitazione, vento ora per ora). Nessun servizio nuovo.
- **Disegno:** nostro, nello stile dell'app (non l'immagine Meteoblue, che richiede un
  pacchetto a pagamento e ha sfondo bianco): curva della temperatura con fasce giorno/notte,
  barre della pioggia, velocità e frecce del vento, linea dell'ora attuale.
- **Dove:** pulsante sulla card meteo → pagina dedicata; tocco su un giorno → pagina del giorno.

## 2. Allerte meteo (severe weather warnings)

- **Fonte:** Meteoalarm (EUMETNET), la stessa mostrata da Meteoblue. Gratuita con citazione
  della fonte. Da verificare: formato del feed attuale (Atom/CAP) e condizioni d'uso.
- **Punto delicato:** associare la località scelta alla zona di allerta (area/provincia).
- **Dove:** badge colorato (giallo/arancio/rosso) sulle schede dei giorni interessati, riga
  nella pagina del giorno, tocco → dettaglio con validità "dalle… alle…", descrizione e
  istruzioni (italiano/inglese).

## 3. Radar

- **Preferenza dell'utente:** lo stile grafico del radar di **windy.com**.
- **Opzioni da verificare (condizioni d'uso e costi):**
  - Windy: widget incorporabile (embed, livello radar) in una WebView, oppure la Map
    Forecast API (chiave; la versione di prova potrebbe avere limiti, la produzione è a
    pagamento).
  - Alternative gratuite: RainViewer (radar mondiale, ultime ~2 h + previsione 30 min;
    condizioni del servizio gratuito cambiate di recente) o radar della Protezione Civile
    (solo Italia, dati aperti), con mappa di base OpenStreetMap.
- **Dove:** pulsante sulla card meteo → pagina radar animata, centrata sulla località.

## Note

- Dall'ambiente cloud di sviluppo questi servizi non sono raggiungibili (proxy): indirizzi
  e condizioni vanno verificati prima di implementare.
