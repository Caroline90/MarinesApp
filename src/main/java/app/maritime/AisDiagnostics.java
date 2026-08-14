package app.maritime;

public record AisDiagnostics(String connectionState, String subscriptionState, long aisMessageCounter,
                             long positionReportCounter, String lastAisMessageType, String lastAisError,
                             long redisVesselCount) {
}
