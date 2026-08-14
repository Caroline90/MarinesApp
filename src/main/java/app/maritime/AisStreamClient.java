package app.maritime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@org.springframework.stereotype.Component
final class AisStreamClient {
    private static final Duration NO_DATA_FALLBACK_DELAY = Duration.ofSeconds(20);

    private final AisStreamProperties properties;
    private final AisDiagnosticsService diagnostics;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    AisStreamClient(AisStreamProperties properties, AisDiagnosticsService diagnostics, ObjectMapper objectMapper) {
        this.properties = properties;
        this.diagnostics = diagnostics;
        this.objectMapper = objectMapper;
    }

    void start() {
        if (!properties.enabled()) {
            diagnostics.connectionState("DISABLED");
            diagnostics.subscriptionState("DISABLED");
            return;
        }
        if (properties.apiKey().isBlank()) {
            diagnostics.connectionState("WAITING_FOR_API_KEY");
            diagnostics.subscriptionState("NOT_SUBSCRIBED");
            diagnostics.error("Set AISSTREAM_API_KEY or aisstream.api-key to connect.");
            seedDemoVessel();
            return;
        }
        diagnostics.connectionState("CONNECTING");
        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .buildAsync(URI.create(properties.url()), new Listener())
                .exceptionally(error -> {
                    diagnostics.connectionState("ERROR");
                    diagnostics.subscriptionState("NOT_SUBSCRIBED");
                    diagnostics.error("AISStream connection failed: " + error.getMessage());
                    return null;
                });
        scheduleNoDataFallback();
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
                .formatted(properties.apiKey(), properties.minLatitude(), properties.minLongitude(), properties.maxLatitude(), properties.maxLongitude());
    }

    private void handleMessage(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String type = text(root, "MessageType").orElse("unknown");
            diagnostics.recordMessage(type);
            JsonNode positionReport = root.path("Message").path("PositionReport");
            if (positionReport.isMissingNode()) return;
            String mmsi = text(root.path("MetaData"), "MMSI")
                    .or(() -> text(positionReport, "UserID"))
                    .orElse("unknown");
            String name = text(root.path("MetaData"), "ShipName")
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .orElse("MMSI " + mmsi);
            diagnostics.recordPosition(new VesselPosition(mmsi, name,
                    number(positionReport, "Latitude"), number(positionReport, "Longitude"),
                    number(positionReport, "Sog"), number(positionReport, "Cog"), type, Instant.now()));
        } catch (Exception error) {
            diagnostics.error("AISStream message parse error: " + error.getMessage());
        }
    }

    private void scheduleNoDataFallback() {
        if (!properties.demoFallbackEnabled()) return;
        CompletableFuture.delayedExecutor(NO_DATA_FALLBACK_DELAY.toSeconds(), java.util.concurrent.TimeUnit.SECONDS).execute(() -> {
            if (diagnostics.positionReportCount() > 0) return;
            diagnostics.error("No live AIS position reports received yet; showing labeled demo vessel until the stream delivers data.");
            seedDemoVessel();
        });
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return Optional.empty();
        return Optional.of(value.asText());
    }

    private static double number(JsonNode node, String field) { return node.path(field).asDouble(0.0); }

    private void seedDemoVessel() { diagnostics.recordPosition(new VesselPosition("261000001", "Świnoujście Demo", 53.912, 14.254, 0, 0, "DemoPositionReport", Instant.now())); }
}
