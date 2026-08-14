package app.maritime;

import java.time.Instant;

public record VesselPosition(String mmsi, String name, double latitude, double longitude,
                             double speedOverGround, double courseOverGround, String messageType,
                             Instant receivedAt) {
}
