package io.dws.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the generic Open Workflow Specification orchestrator. Ships as the prebuilt
 * {@code sw-orchestrator} image and executes workflow definitions supplied as configuration;
 * no per-workflow code is generated.
 */
@SpringBootApplication
public class OrchestratorApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrchestratorApplication.class, args);
  }
}
