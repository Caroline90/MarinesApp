package app.maritime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("/api/v1/system/ais")
final class AisController {
    private final AisDiagnosticsService diagnostics;

    AisController(AisDiagnosticsService diagnostics) {
        this.diagnostics = diagnostics;
    }

    @GetMapping
    AisDiagnostics diagnostics() {
        return diagnostics.diagnostics();
    }

    @GetMapping("/vessels")
    Collection<VesselPosition> vessels() {
        return diagnostics.vessels();
    }
}
