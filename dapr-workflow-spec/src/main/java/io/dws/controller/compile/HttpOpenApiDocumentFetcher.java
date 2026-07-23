package io.dws.controller.compile;

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
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new DocumentFetchException(
                        "OpenAPI document fetch returned HTTP " + response.statusCode() + " for " + url, null);
            }
            return response.body();
        } catch (IOException e) {
            throw new DocumentFetchException("Failed to fetch OpenAPI document " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DocumentFetchException("Interrupted fetching OpenAPI document " + url, e);
        }
    }
}
