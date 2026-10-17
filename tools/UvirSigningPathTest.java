import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;

/** Standalone resolver checks; optional live validation reads the key but signs nothing. */
class UvirSigningPathTest {
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Signing path check failed");
    }

    private static void rejects(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Relative signing path was accepted");
    }

    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("uvir-signing-test-home-").toAbsolutePath();
        Path expected = home.resolve("Dropbox/Mondiversi/UvirSigning/uvir-release.jks");
        Properties properties = new Properties();
        check(ReleaseTool.signingKeyPath(properties, home.toString(), Map.of()).equals(expected));
        properties.setProperty("storeFile", "   ");
        check(ReleaseTool.signingKeyPath(properties, home.toString(), Map.of()).equals(expected));
        Path custom = home.resolve("custom signing");
        check(ReleaseTool.signingKeyPath(properties, home.toString(),
            Map.of("MONDIVERSI_SIGNING_DIR", custom.toString())).equals(custom.resolve("uvir-release.jks")));
        Path explicit = home.resolve("explicit.jks");
        properties.setProperty("storeFile", explicit.toString());
        check(ReleaseTool.signingKeyPath(properties, home.toString(),
            Map.of("MONDIVERSI_SIGNING_DIR", "relative-invalid-directory")).equals(explicit));
        properties.setProperty("storeFile", "relative.jks");
        rejects(() -> ReleaseTool.signingKeyPath(properties, home.toString(), Map.of()));
        properties.clear();
        rejects(() -> ReleaseTool.signingKeyPath(properties, home.toString(),
            Map.of("MONDIVERSI_SIGNING_DIR", "relative-directory")));
        Path cleanProject = Files.createDirectory(home.resolve("clean-project"));
        Path sharedDirectory = Files.createDirectories(home.resolve("Dropbox/Mondiversi/UvirSigning"));
        Files.writeString(sharedDirectory.resolve("signing.properties"),
            "storeFile=\nstorePassword=fixture-store\nkeyAlias=fixture-alias\nkeyPassword=fixture-key\n");
        Properties sharedOnly = ReleaseTool.signingProperties(cleanProject, home.toString(), Map.of());
        check(sharedOnly.getProperty("keyAlias").equals("fixture-alias"));
        check(sharedOnly.getProperty("storePassword").equals("fixture-store"));
        check(sharedOnly.getProperty("keyPassword").equals("fixture-key"));
        check(!Files.exists(cleanProject.resolve("keystore.properties")));
        Files.writeString(cleanProject.resolve("keystore.properties"), "keyAlias=fixture-local\n");
        Properties localOverride = ReleaseTool.signingProperties(cleanProject, home.toString(), Map.of());
        check(localOverride.getProperty("keyAlias").equals("fixture-local"));
        check(localOverride.getProperty("storePassword").equals("fixture-store"));
        System.out.println("PASS: portable paths, override precedence/rejection, Dropbox-only configuration on a clean project, optional local overrides");

        if (args.length == 1 && args[0].equals("--live")) {
            Path emptyProject = Files.createTempDirectory("uvir-shared-only-live-");
            Properties actual = ReleaseTool.signingProperties(emptyProject);
            Path key = ReleaseTool.signingKeyPath(actual, System.getProperty("user.home"), System.getenv());
            check(Files.isRegularFile(key));
            char[] storePassword = actual.getProperty("storePassword").toCharArray();
            char[] keyPassword = actual.getProperty("keyPassword").toCharArray();
            try {
                KeyStore store = KeyStore.getInstance(key.toFile(), storePassword);
                String alias = actual.getProperty("keyAlias");
                check(store.getKey(alias, keyPassword) instanceof PrivateKey);
                String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(store.getCertificate(alias).getEncoded()));
                check(fingerprint.equals("9ddb005e9cf9758530f89666b9ab2801691608103194dd4cee50465e8f00741c"));
                System.out.println("PASS: real Dropbox-only credentials open the existing private key without a project-local configuration; certificate unchanged; nothing signed");
            } finally {
                Arrays.fill(storePassword, '\0');
                Arrays.fill(keyPassword, '\0');
                actual.clear();
            }
        }
    }
}
