package io.dws.controller.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** One canonical external host plus OAuth2 policy, shared by every equivalent requesting step. */
public record OAuthEndpoint(
    String name,
    String baseUrl,
    Set<String> paths,
    Set<String> appIds,
    OAuthMiddleware middleware) {

  public OAuthEndpoint {
    paths = Collections.unmodifiableSet(new LinkedHashSet<>(paths));
    appIds = Collections.unmodifiableSet(new LinkedHashSet<>(appIds));
  }
}
