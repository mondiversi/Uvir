# Uvir Desktop

Avvio:

```powershell
py uvir_desktop.py
```

Non è necessario creare un file EXE. Lo script usa soltanto moduli inclusi in
Python. ADB è necessario soltanto per USB e per la modalità tecnica presente
nella scheda **Avanzate**.

L’interfaccia rileva automaticamente la lingua del sistema: usa l’italiano sui
sistemi configurati in italiano e l’inglese in tutti gli altri casi. Anche le
intestazioni e la legenda dei file CSV, Excel e LibreOffice seguono la lingua
rilevata.

## Memoria locale

Indirizzi Wi-Fi e Bluetooth, codici di collegamento, dati del Debug wireless,
ultimo metodo di connessione, parametri AUTO e ultimo database aperto vengono
salvati automaticamente in:

```text
%USERPROFILE%\UvirDesktop\desktop_settings.json
```

Il file è locale, leggibile e separato dalle misurazioni. I codici vi sono
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

Nell'elenco principale, le misurazioni della stessa acquisizione automatica
sono raccolte sotto un'unica intestazione con data di inizio e quantità. Le
misurazioni manuali e i vecchi dati senza identificativo di sessione restano
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

Le copie scaricate vengono salvate in:

```text
%USERPROFILE%\UvirDesktop\phone_db\uvir.db
```

Prima di sostituzioni, modifiche o cancellazioni viene creata una copia nella
cartella `backups`. Il programma può modificare le note, eliminare misurazioni,
svuotare lo storico e sincronizzare sul telefono l'intero database locale. Le
informazioni che raggruppano le misurazioni della stessa sessione automatica
vengono conservate nelle copie, nelle esportazioni e durante la sincronizzazione.
L'ID della sessione è riportato subito dopo l'ID della misurazione nei file CSV,
Excel e LibreOffice. Dopo una cancellazione totale, gli ID già utilizzati non
vengono assegnati nuovamente. Gli ID delle sessioni automatiche sono progressivi
(`1`, `2`, `3`...) e indipendenti dagli ID delle singole misurazioni.

Il comando **Azzera contatori…** elimina tutte le misurazioni e riporta a zero
entrambi i contatori; la misurazione e la sessione successive ripartono quindi
dall'ID 1. Prima dell'operazione viene sempre richiesta una conferma e, per i
database locali, viene creato un backup.

Package Android previsto:

```text
me.mondiversi.uvir
```
