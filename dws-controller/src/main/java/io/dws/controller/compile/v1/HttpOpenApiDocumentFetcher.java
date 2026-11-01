package io.dws.controller.compile.v1;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Default fetcher: an HTTP GET via the JDK HttpClient. */
@ApplicationScoped
public class HttpOpenApiDocumentFetcher implements OpenApiDocumentFetcher {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

  @Override
  public byte[] fetch(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      // Its own message quotes the whole string, query and all.
      throw new DocumentFetchException("Invalid OpenAPI document URL", null);
    }
    HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
    String shown = redact(uri);
    try {
      HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() / 100 != 2) {
        throw new DocumentFetchException(
            "OpenAPI document fetch returned HTTP " + response.statusCode() + " for " + shown,
            null);
      }
      return response.body();
    } catch (IOException e) {
      throw new DocumentFetchException("Failed to fetch OpenAPI document " + shown, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new DocumentFetchException("Interrupted fetching OpenAPI document " + shown, e);
    }
  }

  /**
   * The URL as it may appear in an exception message, which Quarkus logs for an unhandled error:
   * without user-info, query or fragment, any of which can carry a credential.
   */
  static String redact(URI uri) {
    String authority = uri.getHost() == null ? "" : uri.getHost();
    if (uri.getPort() != -1) {
      authority += ":" + uri.getPort();
    }
    return (uri.getScheme() == null ? "" : uri.getScheme() + "://")
        + authority
        + (uri.getRawPath() == null ? "" : uri.getRawPath());
  }
}
