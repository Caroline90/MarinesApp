package app.maritime;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class AisDiagnosticsService {
    private final AtomicLong aisMessageCounter = new AtomicLong();
    private final AtomicLong positionReportCounter = new AtomicLong();
    private final Map<String, VesselPosition> vessels = new ConcurrentHashMap<>();
    private volatile String connectionState = "DISCONNECTED";
    private volatile String subscriptionState = "NOT_SUBSCRIBED";
    private volatile String lastAisMessageType = "none";
    private volatile String lastAisError = "none";

    void connectionState(String state) { connectionState = state; }
    void subscriptionState(String state) { subscriptionState = state; }
    void error(String message) { lastAisError = message == null || message.isBlank() ? "unknown AIS error" : message; }
    void recordMessage(String type) { aisMessageCounter.incrementAndGet(); lastAisMessageType = blank(type, "unknown"); }
    void recordPosition(VesselPosition position) { positionReportCounter.incrementAndGet(); vessels.put(position.mmsi(), position); }
    AisDiagnostics diagnostics() { return new AisDiagnostics(connectionState, subscriptionState, aisMessageCounter.get(), positionReportCounter.get(), lastAisMessageType, lastAisError, vessels.size()); }
    Collection<VesselPosition> vessels() { return vessels.values().stream().sorted(Comparator.comparing(VesselPosition::receivedAt).reversed()).limit(500).toList(); }
    private static String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
