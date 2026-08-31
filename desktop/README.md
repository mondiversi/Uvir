# Uvir Desktop

Avvio su Windows:

```powershell
py uvir_desktop.py
```

Avvio su Linux o macOS:

```bash
python3 uvir_desktop.py
```

Non è necessario creare un file EXE. Lo script usa soltanto moduli inclusi in
Python. ADB è necessario soltanto per USB e per la modalità tecnica presente
nella scheda **Avanzate**.

L’interfaccia rileva automaticamente la lingua del sistema: usa l’italiano sui
sistemi configurati in italiano e l’inglese in tutti gli altri casi. Le
esportazioni CSV, Excel e LibreOffice vengono invece generate sempre in
inglese, con data ISO e separatore decimale internazionale. Le traduzioni
dell’interfaccia si trovano nei file `lang/it.json` e `lang/en.json`,
accanto allo script, così è possibile aggiornarle senza modificare il codice
Python.

## Memoria locale

Indirizzi Wi-Fi e Bluetooth, codici di collegamento, dati del Debug wireless,
ultimo metodo di connessione, parametri AUTO e ultimo database aperto vengono
salvati automaticamente in:

nel file `desktop_settings.json`, nella stessa cartella di
`uvir_desktop.py`. Il percorso resta quindi portabile su Windows, Linux e
macOS insieme allo script.

Il file è locale, leggibile e separato dalle acquisizioni. I codici vi sono
salvati in chiaro per poter compilare automaticamente i campi al riavvio; non
vengono inviati altrove dal programma.

## Dati LIVE sul computer

Dopo il collegamento si apre automaticamente **Uvir LIVE**. La finestra mostra
ogni mezzo secondo i canali UVC, UVB, UVA, visibile, far-red e NIR ricevuti dal
telefono, insieme ai totali, alle stime biologiche e allo stato dell'acquisizione
automatica. Si può riaprire in qualsiasi momento con **Apri LIVE** nella
barra principale del programma. I comandi di salvataggio e acquisizione
automatica si trovano soltanto in **Acquisizione…** dentro la finestra LIVE;
non sono duplicati nella finestra di collegamento.

Nell'elenco principale, le acquisizioni della stessa sessione automatica
sono raccolte sotto un'unica intestazione con data di inizio e quantità. Le
acquisizioni manuali e i vecchi dati senza identificativo di sessione restano
visualizzati singolarmente.

## Modalità di collegamento

### USB

1. Attivare Debug USB sul telefono.
2. Collegare il telefono e autorizzare il computer.
3. Aprire Uvir Desktop, premere **Collega telefono** e scegliere **USB**.

La connessione usa un tunnel locale ADB e non richiede il codice di
abbinamento Uvir.

### Wi-Fi

1. Collegare telefono e computer alla stessa rete Wi-Fi privata.
2. In Uvir aprire **Impostazioni > Collega con Wi-Fi**.
3. Nella scheda **Wi-Fi** del programma desktop inserire l’indirizzo Wi-Fi e il codice
   mostrati dal telefono. La porta è già configurata e non va inserita.

### Bluetooth

1. Associare PC e telefono via Bluetooth.
2. Attivare sul telefono il tethering Bluetooth e collegare il PC alla sua rete.
3. In Uvir aprire **Impostazioni > Collega con Bluetooth**.
4. Nella scheda **Bluetooth** del programma desktop inserire l’indirizzo Bluetooth e il
   codice mostrati dal telefono.

Il protocollo Uvir non richiede librerie Python Bluetooth esterne.

### Debug wireless (avanzato)

1. Telefono e PC devono essere sulla stessa rete Wi-Fi.
2. In **Opzioni sviluppatore > Debug wireless**, usare prima indirizzo e codice
   mostrati da **Abbina dispositivo con codice**.
3. Inserire poi l'indirizzo IP e la porta della schermata principale del debug
   wireless e premere **Collega con Debug wireless** nella scheda **Avanzate**.

Usare l'accesso diretto soltanto su reti private affidabili. Il collegamento è
protetto dal codice di abbinamento ma non è destinato all'esposizione diretta
su Internet. In modalità diretta il processo di Uvir deve rimanere attivo sul
telefono; con USB o ADB wireless lo script può avviare automaticamente l'app.

## Database e backup

Lo schema corrente usa la tabella interna `acquisitions`. Quando viene aperto
un vecchio database desktop con la tabella `measurements`, il programma la
rinomina automaticamente conservando i record.

Le copie scaricate vengono salvate in:

```text
%USERPROFILE%\UvirDesktop\phone_db\uvir.db
```

Prima di sostituzioni, modifiche o cancellazioni viene creata una copia nella
cartella `backups`. Il programma può modificare le note, eliminare acquisizioni,
svuotare lo storico e sincronizzare sul telefono l'intero database locale. Le
informazioni che raggruppano le acquisizioni della stessa sessione automatica
vengono conservate nelle copie, nelle esportazioni e durante la sincronizzazione.
L'ID della sessione è riportato subito dopo l'ID dell'acquisizione nei file CSV,
Excel e LibreOffice. Dopo una cancellazione totale, gli ID già utilizzati non
vengono assegnati nuovamente. Gli ID delle sessioni automatiche sono progressivi
(`1`, `2`, `3`...) e indipendenti dagli ID delle singole acquisizioni.

CSV, Excel e LibreOffice usano lo stesso ordine di colonne e gli stessi campi.
Anche il CSV condiviso dal telefono segue esattamente questo schema; la tabella
leggibile resta volutamente più discorsiva. Tutti i file esportati e la tabella
leggibile Android sono sempre in inglese, indipendentemente dalla lingua
dell’interfaccia. La tabella leggibile viene condivisa come allegato `.txt`,
anziché come corpo del messaggio, per evitare i limiti di lunghezza di WhatsApp
e di altre applicazioni.

Il comando **Azzera contatori…** elimina tutte le acquisizioni e riporta a zero
entrambi i contatori; l'acquisizione e la sessione successive ripartono quindi
dall'ID 1. Prima dell'operazione viene sempre richiesta una conferma e, per i
database locali, viene creato un backup.

Package Android previsto:

```text
me.mondiversi.uvir
```
