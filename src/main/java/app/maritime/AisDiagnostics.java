package app.maritime;

record AisDiagnostics(String connectionState, String subscriptionState, long aisMessageCounter,
                      long positionReportCounter, String lastAisMessageType, String lastAisError,
                      int redisVesselCount) {
}
