import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;

/** Offline signing. Keystore and passwords never leave the local signing machine. */
public class ReleaseTool {
    static String digest(Path file) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[32768]; int count;
            while ((count = in.read(buffer)) != -1) sha.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(sha.digest());
    }
    static String asset(Path file, String base) throws Exception {
        return "\"url\":\"" + base + file.getFileName() + "\",\"bytes\":" + Files.size(file) + ",\"sha256\":\"" + digest(file) + "\"";
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 6) throw new IllegalArgumentException("root output firmware-build app-version app-code firmware-version");
        Path root = Path.of(args[0]), out = Path.of(args[1]), build = Path.of(args[2]);
        String version = args[3], sensor = args[5]; int code = Integer.parseInt(args[4]);
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || !sensor.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IllegalArgumentException("Invalid release version");
        Files.createDirectories(out);
        Path apk = out.resolve("Uvir-" + version + ".apk");
        Path bin = out.resolve("UvirSensor-" + sensor + "-esp32.bin");
        Files.copy(root.resolve("app/build/outputs/apk/release/app-release.apk"), apk, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(build.resolve("UvirSensor.ino.bin"), bin, StandardCopyOption.REPLACE_EXISTING);
        if (Files.size(bin) > 0x300000) throw new IllegalArgumentException("Firmware exceeds application partition");
        String base = "https://github.com/mondiversi/Uvir/releases/download/v" + version + "/";
        byte[] partition = Files.readAllBytes(build.resolve("UvirSensor.ino.partitions.bin"));
        if (partition.length != 3072) throw new IllegalArgumentException("Unexpected partition table size");
        String partitionMd5 = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(partition));
        String payload = "{\"schema\":1,\"repository\":\"mondiversi/Uvir\",\"published_at\":\"" + Instant.now() + "\",\"app\":{\"package\":\"me.mondiversi.uvir\",\"version\":\"" + version + "\",\"code\":" + code + ",\"min_sdk\":26," + asset(apk, base) + "},\"sensor\":{\"version\":\"" + sensor + "\",\"min_app_code\":" + code + ",\"chip\":\"ESP32\",\"layout\":\"uvir-esp32-huge-app-v1\",\"offset\":65536,\"capacity\":3145728,\"partition_md5\":\"" + partitionMd5 + "\"," + asset(bin, base) + "}}";
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(root.resolve("keystore.properties"))) { properties.load(input); }
        KeyStore keys = KeyStore.getInstance("JKS");
        try (InputStream input = Files.newInputStream(Path.of(properties.getProperty("storeFile")))) { keys.load(input, properties.getProperty("storePassword").toCharArray()); }
        String alias = properties.getProperty("keyAlias");
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign((PrivateKey)keys.getKey(alias, properties.getProperty("keyPassword").toCharArray()));
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8); signer.update(bytes); byte[] signature = signer.sign();
        Signature verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(keys.getCertificate(alias).getPublicKey()); verifier.update(bytes);
        if (!verifier.verify(signature)) throw new SecurityException("Release signature verification failed");
        Files.writeString(out.resolve("uvir-update.json"), "{\"algorithm\":\"SHA256withRSA\",\"payload\":\"" + Base64.getEncoder().encodeToString(bytes) + "\",\"signature\":\"" + Base64.getEncoder().encodeToString(signature) + "\"}\n");
        Path zip = out.resolve("UvirSensor-" + sensor + "-usb.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String name : List.of("UvirSensor.ino.bin", "UvirSensor.ino.bootloader.bin", "UvirSensor.ino.partitions.bin")) {
                output.putNextEntry(new ZipEntry(name)); Files.copy(build.resolve(name), output); output.closeEntry();
            }
            output.putNextEntry(new ZipEntry("README.txt"));
            output.write(("Uvir Sensor " + sensor + " — classic ESP32, huge_app 3 MiB.\n\nEXISTING UVIR SENSOR: synchronize all records and stop every session first.\nPreserving update (application ONLY):\npython -m esptool --chip esp32 --port COM6 write-flash 0x10000 UvirSensor.ino.bin\nReplace COM6 with your serial port. Never erase-flash.\n\nFIRST INSTALL ONLY, after confirming the hardware and flash layout:\npython -m esptool --chip esp32 --port COM6 write-flash 0x1000 UvirSensor.ino.bootloader.bin 0x8000 UvirSensor.ino.partitions.bin 0x10000 UvirSensor.ino.bin\nDo not use the first-install command to update an existing sensor.\n\nKeep USB power connected. Hold BOOT if automatic entry fails.\nIf a single-partition update is interrupted, repeat the application-only command.\nSettings (NVS), microSD and FRAM are not erased by application-only updates.\nPhysical AS7331 UV validation is pending.\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        StringBuilder sums = new StringBuilder();
        for (Path file : List.of(apk, bin, zip, out.resolve("uvir-update.json"))) sums.append(digest(file)).append("  ").append(file.getFileName()).append('\n');
        Files.writeString(out.resolve("SHA256SUMS.txt"), sums);
        System.out.println("Release assets generated and signature verified: " + out);
    }
}
