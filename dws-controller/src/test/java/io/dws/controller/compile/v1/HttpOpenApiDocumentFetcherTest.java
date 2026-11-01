package io.dws.controller.compile.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpOpenApiDocumentFetcherTest {

  @Test
  @DisplayName("redact keeps scheme, host, port and path, and drops user-info, query and fragment")
  void redactDropsCredentialCarryingParts() {
    URI uri =
        URI.create("https://svc:s3cret@docs.example.com:8443/api/v1/openapi.yaml?api_key=k#frag");

    assertThat(HttpOpenApiDocumentFetcher.redact(uri))
        .isEqualTo("https://docs.example.com:8443/api/v1/openapi.yaml");
  }

  @Test
  @DisplayName("a malformed URL fails without quoting it")
  void malformedUrlIsNotQuoted() {
    assertThatThrownBy(() -> new HttpOpenApiDocumentFetcher().fetch("https://h/doc?token=abc def"))
        .isInstanceOf(OpenApiDocumentFetcher.DocumentFetchException.class)
        .hasMessageNotContaining("abc");
  }
}
