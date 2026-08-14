package app.maritime;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class MaritimeIntelligenceApplication {
    public static void main(String[] args) throws IOException {
        AppConfig config = AppConfig.load();
        AisDiagnosticsService diagnostics = new AisDiagnosticsService();
        new AisStreamClient(config, diagnostics).start();

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/api/v1/system/ais/vessels", exchange -> json(exchange, vesselsJson(diagnostics)));
        server.createContext("/api/v1/system/ais", exchange -> json(exchange, diagnosticsJson(diagnostics.diagnostics())));
        server.createContext("/", MaritimeIntelligenceApplication::staticResource);
        server.start();
        System.out.printf("Maritime Intelligence v0.7 running at http://localhost:%d%n", config.port());
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
    }

    private static void staticResource(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath().equals("/") ? "/static/index.html" : "/static" + exchange.getRequestURI().getPath();
        try (InputStream input = MaritimeIntelligenceApplication.class.getResourceAsStream(path)) {
            if (input == null) { exchange.sendResponseHeaders(404, -1); return; }
            byte[] bytes = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType(path));
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
        }
    }

    private static String diagnosticsJson(AisDiagnostics d) {
        return "{\"connectionState\":%s,\"subscriptionState\":%s,\"aisMessageCounter\":%d,\"positionReportCounter\":%d,\"lastAisMessageType\":%s,\"lastAisError\":%s,\"redisVesselCount\":%d}"
                .formatted(q(d.connectionState()), q(d.subscriptionState()), d.aisMessageCounter(), d.positionReportCounter(), q(d.lastAisMessageType()), q(d.lastAisError()), d.redisVesselCount());
    }

    private static String vesselsJson(AisDiagnosticsService diagnostics) {
        return diagnostics.vessels().stream().map(v -> "{\"mmsi\":%s,\"name\":%s,\"latitude\":%s,\"longitude\":%s,\"speedOverGround\":%s,\"courseOverGround\":%s,\"messageType\":%s,\"receivedAt\":%s}"
                .formatted(q(v.mmsi()), q(v.name()), v.latitude(), v.longitude(), v.speedOverGround(), v.courseOverGround(), q(v.messageType()), q(v.receivedAt().toString()))).reduce("[", (a, b) -> a.equals("[") ? a + b : a + "," + b) + "]";
    }

    private static String q(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    private static String contentType(String path) { return path.endsWith(".css") ? "text/css" : path.endsWith(".js") ? "text/javascript" : "text/html; charset=utf-8"; }
}
