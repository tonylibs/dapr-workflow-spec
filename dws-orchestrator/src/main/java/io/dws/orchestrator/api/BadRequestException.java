package io.dws.orchestrator.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Maps to HTTP 400 for malformed requests (e.g. invalid JSON bodies). */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {
  public BadRequestException(String message) {
    super(message);
  }
}
