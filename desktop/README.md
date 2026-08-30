# Uvir Desktop

Avvio:

```powershell
py uvir_desktop.py
```

Non è necessario creare un file EXE. Lo script usa soltanto moduli inclusi in
Python. ADB è necessario soltanto per USB e per la modalità tecnica presente
nella scheda **Avanzate**.

## Dati LIVE sul computer

Dopo il collegamento si apre automaticamente **Uvir LIVE**. La finestra mostra
ogni mezzo secondo i canali UVC, UVB, UVA, visibile, far-red e NIR ricevuti dal
telefono, insieme ai totali, alle stime biologiche e allo stato dell'acquisizione
automatica. Si può riaprire in qualsiasi momento con **Apri LIVE** nella
barra principale del programma. I comandi di salvataggio e acquisizione
automatica si trovano soltanto in **Acquisizione…** dentro la finestra LIVE;
non sono duplicati nella finestra di collegamento.

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
svuotare lo storico e sincronizzare sul telefono l'intero database locale.

Package Android previsto:

```text
me.mondiversi.uvir
```
