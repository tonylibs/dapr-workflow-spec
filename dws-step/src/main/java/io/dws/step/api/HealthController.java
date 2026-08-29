package io.dws.step.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness/readiness endpoint for the shared DWS step-service probe contract. */
@RestController
public class HealthController {

  @GetMapping("/healthz")
  public Map<String, String> healthz() {
    return Map.of("status", "ok");
  }
}
