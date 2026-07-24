package io.dws.controller.e2e;

import io.dws.controller.compile.OpenApiDocumentFetcher;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;

/**
 * CDI alternative that replaces {@code HttpOpenApiDocumentFetcher} for the whole test run,
 * so {@code call: openapi} catalog cases compile deterministically offline. The compiler
 * only hashes the returned bytes ({@code DOCUMENT_SHA256}); it never parses them, so a
 * fixed payload suffices. No other test fetches OpenAPI documents over HTTP.
 */
@Mock
@ApplicationScoped
public class FixtureOpenApiDocumentFetcher implements OpenApiDocumentFetcher {

    static final byte[] DOCUMENT = "e2e-openapi-document".getBytes(StandardCharsets.UTF_8);

    @Override
    public byte[] fetch(String url) {
        return DOCUMENT;
    }
}
