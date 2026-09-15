# Distribuzione di Uvir

## Identità definitiva

- Nome visibile: `Uvir`
- Application ID: `me.mondiversi.uvir`
- Namespace: `me.mondiversi.uvir`
- Versione corrente: `1.1.0` (`versionCode` 2)

## Creazione dell'APK

Fare doppio clic su `CREA_APK_RELEASE.bat`.

Al termine, l'APK firmato e pronto per la distribuzione si trova in:

```text
dist\Uvir-1.1.0-release.apk
```

Nella stessa cartella viene generato anche il file
`Uvir-1.1.0-release.apk.sha256`, utile per verificare che il download non sia
stato alterato o danneggiato.

Il file può essere allegato a una GitHub Release oppure distribuito da un sito.
Non è necessario pubblicare il progetto sorgente per distribuire l'APK.

## Firma e aggiornamenti

La chiave privata è conservata fuori dal progetto in:

```text
C:\Users\otta8\Documents\UvirSigning\uvir-release.jks
```

La configurazione locale con le credenziali si trova in `keystore.properties`.
Entrambi devono essere conservati in un backup privato e sicuro. Non devono
essere caricati su GitHub, condivisi o inclusi in archivi pubblici.

Il certificato pubblico esportato si trova in:

```text
C:\Users\otta8\Documents\UvirSigning\uvir-release-certificate.pem
```

Il certificato pubblico può essere condiviso; la chiave `.jks` no.

Per pubblicare un aggiornamento occorre:

1. aumentare `versionCode` in `app/build.gradle.kts`;
2. aggiornare `versionName`;
3. usare sempre la stessa chiave release;
4. ricreare l'APK con `CREA_APK_RELEASE.bat`.

Sul computer di sviluppo, anche la variante `debug` avviata da Android Studio
usa la stessa chiave quando `keystore.properties` è presente. In questo modo
**Run** e **Debug** possono aggiornare l'app già installata senza conflitti di
certificato. Sugli altri computer, dove la configurazione privata non esiste,
Gradle torna automaticamente alla normale chiave debug locale.

Se la chiave release o la sua password vengono perse, gli APK futuri non
potranno aggiornare l'app già installata.
