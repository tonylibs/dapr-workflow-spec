package io.dws.controller.compile;

/**
 * Fetches the raw bytes of an OpenAPI document referenced by a {@code call: openapi} task,
 * so the compiler can pin it by content hash. Abstracted for testability (mock in unit tests).
 */
public interface OpenApiDocumentFetcher {

    /**
     * @param url the document URL
     * @return the document's raw bytes
     * @throws DocumentFetchException if the document cannot be retrieved
     */
    byte[] fetch(String url);

    class DocumentFetchException extends RuntimeException {
        public DocumentFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
