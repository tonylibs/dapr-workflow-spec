package io.dws.step;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the generic host of one pinned Step definition. */
@SpringBootApplication
public class StepApplication {

  public static void main(String[] args) {
    SpringApplication.run(StepApplication.class, args);
  }
}
