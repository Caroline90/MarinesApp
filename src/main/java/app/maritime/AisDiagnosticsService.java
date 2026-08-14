package app.maritime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
final class AisDiagnosticsService {
    private static final String VESSELS_KEY = "maritime:vessels";

    private final AtomicLong aisMessageCounter = new AtomicLong();
    private final AtomicLong positionReportCounter = new AtomicLong();
    private final Map<String, VesselPosition> fallbackVessels = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private volatile String connectionState = "DISCONNECTED";
    private volatile String subscriptionState = "NOT_SUBSCRIBED";
    private volatile String lastAisMessageType = "none";
    private volatile String lastAisError = "none";

    AisDiagnosticsService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    void connectionState(String state) { connectionState = state; }
    void subscriptionState(String state) { subscriptionState = state; }
    void error(String message) { lastAisError = message == null || message.isBlank() ? "unknown AIS error" : message; }
    void recordMessage(String type) { aisMessageCounter.incrementAndGet(); lastAisMessageType = blank(type, "unknown"); }
    long positionReportCount() { return positionReportCounter.get(); }

    void recordPosition(VesselPosition position) {
        positionReportCounter.incrementAndGet();
        fallbackVessels.put(position.mmsi(), position);
        try {
            redis.opsForHash().put(VESSELS_KEY, position.mmsi(), objectMapper.writeValueAsString(position));
        } catch (RuntimeException | JsonProcessingException error) {
            this.error("Redis vessel write failed: " + error.getMessage());
        }
    }

    AisDiagnostics diagnostics() {
        return new AisDiagnostics(connectionState, subscriptionState, aisMessageCounter.get(), positionReportCounter.get(),
                lastAisMessageType, lastAisError, vesselCount());
    }

    Collection<VesselPosition> vessels() {
        try {
            return redis.opsForHash().values(VESSELS_KEY).stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(this::readPosition)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(VesselPosition::receivedAt).reversed())
                    .limit(500)
                    .toList();
        } catch (RuntimeException error) {
            this.error("Redis vessel read failed: " + error.getMessage());
            return fallbackVessels.values().stream()
                    .sorted(Comparator.comparing(VesselPosition::receivedAt).reversed())
                    .limit(500)
                    .toList();
        }
    }

    private long vesselCount() {
        try {
            return redis.opsForHash().size(VESSELS_KEY);
        } catch (RuntimeException error) {
            return fallbackVessels.size();
        }
    }

    private VesselPosition readPosition(String json) {
        try {
            return objectMapper.readValue(json, VesselPosition.class);
        } catch (JsonProcessingException error) {
            this.error("Redis vessel parse failed: " + error.getMessage());
            return null;
        }
    }

    private static String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
