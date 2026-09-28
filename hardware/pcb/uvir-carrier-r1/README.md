# Uvir Carrier R1 — EasyEDA Standard

## File da aprire in EasyEDA

`uvir-carrier-r1-easyeda-standard-schematic.json` è uno **schema elettrico
nativo di EasyEDA Standard**. La prima versione apriva i componenti, ma usava
solo brevi spezzoni con nomi di rete: a vista sembravano scollegati. Questa
versione usa fili continui e punti di giunzione espliciti. Contiene 13 simboli,
52 pin, 17 reti e 69 segmenti. Il generatore verifica che ciascuna rete sia
elettricamente continua nel disegno, non soltanto etichettata allo stesso modo.
La nuova versione non è ancora stata reimportata e verificata visivamente
nell'editor EasyEDA.

In EasyEDA **Standard** per PC:

1. Aprire `File > Apri > EasyEDA...`.
2. Selezionare `uvir-carrier-r1-easyeda-standard-schematic.json`.
3. Lasciare selezionato **Importa file** e confermare.

Non aprire il file facendo doppio clic in Windows e non scegliere Altium,
Eagle o KiCad. `uvir-carrier-r1-pin-netlist.json` è la mappa logica di
riferimento, **non** un documento nativo EasyEDA.

## Corrispondenza pin e posizione

Il simbolo ESP32 riporta i numeri di posizione `J2` (fila sinistra) e `J3`
(fila destra), guardando la scheda frontalmente con la USB in basso. Sono
ricavati dalla foto `esp32_pin_ref.jpg` fornita dall'utente e coerenti con
la piedinatura ESP32-DevKitC a 38 pin. La distanza reale tra le file e il
passo vanno comunque misurati sulla scheda prima di progettare gli zoccoli.

Lo **schema elettrico** dispone i blocchi per rendere leggibili i fili; non
rappresenta la posizione fisica sul circuito stampato. L'intenzione di
posizionamento è registrata nella sezione `layout` della mappa logica:

- **Fronte, dal basso:** ESP32 con USB verso il bordo inferiore; sopra
  AS7343 visibile/NIR orizzontale; più in alto UV 5 Click orizzontale;
  in cima buzzer, LED RGB e LED blu. Pulsante laterale a sinistra,
  leggermente sopra la metà.
- **Retro, dal basso:** RTC trasversale; area antenna ESP32 libera;
  FRAM sopra; adattatore microSD in alto con scheda estraibile dal bordo.
- **Ingombri indicativi:** ESP32 25×52 mm; AS7343 22×13 mm;
  UV 5 Click 28,6×25,4 mm; RTC 37×22 mm; FRAM 20×15 mm;
  microSD 24×43 mm. Non bastano per fabbricare la scheda.

Le quattro periferiche I²C condividono GPIO21/SDA, GPIO22/SCL, 3,3 V e GND.
Il modulo microSD usa GPIO13/18/19/23 per SPI e soltanto il suo VCC usa
5 V, secondo l'adattatore con regolatore e conversione di livello già
provato. Ogni LED ha una propria resistenza serie da 330 Ω. Il pulsante
esterno porta GPIO33 a GND quando viene premuto: i terminali scelti sono
su lati opposti del contatto.

## Limiti prima di ordinare la PCB

Questo documento definisce i collegamenti, **non** è una PCB. I simboli
dei moduli non hanno ancora impronte fisiche assegnate. Prima dei Gerber:

- misurare pin 1, ordine, passo e distanza tra le file degli zoccoli
  di ogni modulo, nonché polarità di LED/buzzer e orientamento microSD;
- creare/assegnare le impronte e posizionarle realmente su fronte/retro;
- verificare il budget del regolatore 3,3 V, i pull-up I²C già presenti
  sui moduli, l'isolamento ottico e il divieto di rame sotto l'antenna;
- controllare che la ricarica RTC sia disabilitata con una CR2032;
- eseguire ERC, DRC e revisione umana prima dell'ordine.

`generate_easyeda_standard.py` rigenera il file dal pinout logico e si
ferma se manca un pin, è duplicato, la sua rete non è continua oppure la
mappa GPIO non coincide con `UvirHardwareConfig.h`.
Il generatore effettivo è `generate_connected_easyeda.py`; il primo nome
rimane disponibile per compatibilità.
