package io.dws.controller;

import io.dws.controller.logging.StructuredLogging;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Entry point. The only thing it adds to Quarkus' generated main is choosing the log format before
 * Quarkus starts, so the whole process lifetime, boot included, uses one format.
 */
@QuarkusMain
public class ControllerMain {

  public static void main(String... args) {
    StructuredLogging.install(System.getenv(StructuredLogging.FORMAT_ENV));
    Quarkus.run(args);
  }
}
