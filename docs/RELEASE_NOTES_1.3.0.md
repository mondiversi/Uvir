# Uvir 1.3.0 — firmware sensore 0.5.103

Consolidamento della piattaforma Android/ESP32 e primo sistema di aggiornamento
da GitHub con indice firmato e verifica dei file.

## Novità

- Gestione multisensore: connessioni e registrazioni distinte, con un sensore
  selezionato per visualizzazione e comandi.
- Acquisizioni, allerte, sessioni autonome, comando esterno e varianti di sessione.
- Registrazioni offline in FRAM/microSD e recupero dello stato con RTC DS3231.
- Esportazioni CSV/TXT/PNG, backup cifrati e diagnostica estesa.
- Interfaccia, localizzazioni, accessibilità e selezione sensore rifinite.
- Ricerca automatica e manuale degli aggiornamenti, elenco ordinato delle versioni,
  download verificati, aggiornamento dell’app prima del sensore e log dei fallimenti.
- Aggiornamento USB del firmware con verifica dell’identità, dello stato inattivo
  e dello schema di memoria. Non cancella impostazioni o dati.

## File

- `Uvir-1.3.0.apk`: app Android firmata (Android 8 o successivo).
- `UvirSensor-0.5.103-esp32.bin`: sola applicazione ESP32.
- `UvirSensor-0.5.103-usb.zip`: firmware e istruzioni USB per aggiornamento,
  recupero e prima installazione.
- `uvir-update.json`: indice firmato per il controllo dall’app.
- `SHA256SUMS.txt`: impronte dei file pubblicati.

## Verifiche e limiti

Compilazioni Android e firmware completate; test JVM, test delle logiche firmware
e controlli sul telefono eseguiti. Il sensore reale è stato aggiornato via USB dal
computer, con identità, impostazioni, credenziali e conteggi dei record verificati
prima e dopo, senza creare acquisizioni artificiali.

Il sensore UV AS7331 non è ancora disponibile: la sua validazione fisica e la
calibrazione restano da completare. Il caricamento firmware direttamente dall’app
via USB OTG richiede ancora la prova fisica di quel collegamento; il protocollo,
i blocchi di sicurezza e i file vengono testati separatamente.

**Non scollegare l’alimentazione durante il caricamento firmware.** L’ESP32 usa
una sola area applicativa: non è disponibile un rollback automatico. Per recupero
usare la procedura USB dell’archivio, senza cancellare l’intera flash.

Documentazione: [aggiornamenti e recupero](UPDATES.md),
[interfaccia seriale](USB_SERIAL_PROTOCOL.md).
