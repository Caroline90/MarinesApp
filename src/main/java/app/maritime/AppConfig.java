package app.maritime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

record AppConfig(int port, boolean aisEnabled, String aisUrl, String aisApiKey,
                 double minLongitude, double minLatitude, double maxLongitude, double maxLatitude) {
    static AppConfig load() {
        Properties properties = new Properties();
        try (InputStream input = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (input != null) properties.load(input);
        } catch (IOException ignored) { }
        Path local = Path.of("application.properties");
        if (Files.isRegularFile(local)) {
            try (InputStream input = Files.newInputStream(local)) { properties.load(input); } catch (IOException ignored) { }
        }
        return new AppConfig(
                integer(properties, "server.port", 8080),
                bool(properties, "aisstream.enabled", true),
                text(properties, "aisstream.url", "wss://stream.aisstream.io/v0/stream"),
                envOrProperty(properties, "AISSTREAM_API_KEY", "aisstream.api-key", ""),
                decimal(properties, "aisstream.min-longitude", 14.16),
                decimal(properties, "aisstream.min-latitude", 53.84),
                decimal(properties, "aisstream.max-longitude", 14.42),
                decimal(properties, "aisstream.max-latitude", 54.02));
    }

    private static String envOrProperty(Properties properties, String env, String key, String fallback) {
        String value = System.getenv(env);
        return value == null || value.isBlank() ? text(properties, key, fallback) : value;
    }
    private static String text(Properties properties, String key, String fallback) { return properties.getProperty(key, fallback).replace("${AISSTREAM_API_KEY:}", ""); }
    private static int integer(Properties properties, String key, int fallback) { return Integer.parseInt(text(properties, key, String.valueOf(fallback))); }
    private static double decimal(Properties properties, String key, double fallback) { return Double.parseDouble(text(properties, key, String.valueOf(fallback))); }
    private static boolean bool(Properties properties, String key, boolean fallback) { return Boolean.parseBoolean(text(properties, key, String.valueOf(fallback))); }
}
