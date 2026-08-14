package app.maritime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aisstream")
record AisStreamProperties(boolean enabled, String url, String apiKey,
                           double minLongitude, double minLatitude,
                           double maxLongitude, double maxLatitude,
                           boolean demoFallbackEnabled) {
}
