package app.maritime;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AisStreamClient {
    private static final Pattern MESSAGE_TYPE = Pattern.compile("\\\"MessageType\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern MMSI = Pattern.compile("\\\"MMSI\\\"\\s*:\\s*(?:\\\"([^\\\"]+)\\\"|(\\d+))");
    private static final Pattern SHIP_NAME = Pattern.compile("\\\"ShipName\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
    private static final Pattern LAT = Pattern.compile("\\\"Latitude\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern LON = Pattern.compile("\\\"Longitude\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern SOG = Pattern.compile("\\\"Sog\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern COG = Pattern.compile("\\\"Cog\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");

    private final AppConfig config;
    private final AisDiagnosticsService diagnostics;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    AisStreamClient(AppConfig config, AisDiagnosticsService diagnostics) {
        this.config = config;
        this.diagnostics = diagnostics;
    }

    void start() {
        if (!config.aisEnabled()) {
            diagnostics.connectionState("DISABLED");
            diagnostics.subscriptionState("DISABLED");
            return;
        }
        if (config.aisApiKey().isBlank()) {
            diagnostics.connectionState("WAITING_FOR_API_KEY");
            diagnostics.subscriptionState("NOT_SUBSCRIBED");
            diagnostics.error("Set AISSTREAM_API_KEY or aisstream.api-key to connect.");
            seedDemoVessel();
            return;
        }
        diagnostics.connectionState("CONNECTING");
        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .buildAsync(URI.create(config.aisUrl()), new Listener())
                .exceptionally(error -> {
                    diagnostics.connectionState("ERROR");
                    diagnostics.subscriptionState("NOT_SUBSCRIBED");
                    diagnostics.error("AISStream connection failed: " + error.getMessage());
                    return null;
                });
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder textFrameBuffer = new StringBuilder();

        @Override public void onOpen(WebSocket webSocket) {
            diagnostics.connectionState("CONNECTED");
            webSocket.sendText(subscriptionJson(), true)
                    .thenRun(() -> diagnostics.subscriptionState("SUBSCRIBED"))
                    .exceptionally(error -> { diagnostics.subscriptionState("SUBSCRIBE_ERROR"); diagnostics.error("AISStream subscription failed: " + error.getMessage()); return null; });
            webSocket.request(1);
        }

        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textFrameBuffer.append(data);
            if (last) {
                handleMessage(textFrameBuffer.toString());
                textFrameBuffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override public void onError(WebSocket webSocket, Throwable error) {
            diagnostics.connectionState("ERROR");
            diagnostics.error("AISStream websocket error: " + error.getMessage());
        }

        @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            diagnostics.connectionState("CLOSED");
            diagnostics.subscriptionState("NOT_SUBSCRIBED");
            if (statusCode != WebSocket.NORMAL_CLOSURE) diagnostics.error("AISStream closed with " + statusCode + ": " + reason);
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }
    }

    private String subscriptionJson() {
        return "{\"APIKey\":\"%s\",\"BoundingBoxes\":[[[%s,%s],[%s,%s]]],\"FilterMessageTypes\":[\"PositionReport\"]}"
                .formatted(config.aisApiKey(), config.minLatitude(), config.minLongitude(), config.maxLatitude(), config.maxLongitude());
    }

    private void handleMessage(String json) {
        try {
            String type = find(MESSAGE_TYPE, json).orElse("unknown");
            diagnostics.recordMessage(type);
            if (!json.contains("\"PositionReport\"")) return;
            String mmsi = find(MMSI, json).orElse("unknown");
            String name = find(SHIP_NAME, json).map(String::trim).filter(s -> !s.isBlank()).orElse("MMSI " + mmsi);
            diagnostics.recordPosition(new VesselPosition(mmsi, name, number(LAT, json), number(LON, json), number(SOG, json), number(COG, json), type, Instant.now()));
        } catch (Exception error) {
            diagnostics.error("AISStream message parse error: " + error.getMessage());
        }
    }

    private static Optional<String> find(Pattern pattern, String json) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) return Optional.empty();
        return Optional.ofNullable(matcher.group(1) == null ? matcher.group(2) : matcher.group(1));
    }
    private static double number(Pattern pattern, String json) { return find(pattern, json).map(Double::parseDouble).orElse(0.0); }
    private void seedDemoVessel() { diagnostics.recordPosition(new VesselPosition("261000001", "Świnoujście Demo", 53.912, 14.254, 0, 0, "DemoPositionReport", Instant.now())); }
}
