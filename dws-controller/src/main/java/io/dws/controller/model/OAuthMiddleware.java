package io.dws.controller.model;

import java.util.List;
import java.util.TreeSet;

/** Normalized OAuth2 client-credentials policy consumed by Dapr resource synthesis. */
public record OAuthMiddleware(
    String tokenUrl,
    EnvValue.SecretKeyRef clientId,
    EnvValue.SecretKeyRef clientSecret,
    String clientAuthentication,
    List<String> scopes) {

  public OAuthMiddleware {
    scopes = List.copyOf(new TreeSet<>(scopes));
  }
}
