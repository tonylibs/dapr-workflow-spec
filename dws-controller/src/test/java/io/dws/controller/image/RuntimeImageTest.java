package io.dws.controller.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the runtime image settings cdk8s synthesis depends on. Plain JUnit: reads the Dockerfile the
 * dws-controller workflow builds.
 */
class RuntimeImageTest {

  private static final Path DOCKERFILE = Path.of("src/main/docker/Dockerfile.jvm");

  /**
   * jsii refuses a package cache in a group-writable directory, and the UBI image's home is 0770,
   * so without this every {@code POST /workflows} failed with 500 (scheduling Phase 6a live run).
   */
  @Test
  void disablesTheJsiiPackageCache() throws IOException {
    List<String> lines = Files.readAllLines(DOCKERFILE);

    assertThat(lines).contains("ENV JSII_RUNTIME_PACKAGE_CACHE=disabled");
  }

  @Test
  void installsNodeForJsii() throws IOException {
    assertThat(Files.readString(DOCKERFILE)).contains("microdnf install -y nodejs");
  }
}
