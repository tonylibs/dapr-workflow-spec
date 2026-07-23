package imports.io.dapr;

/**
 * Renegotiation sets the underlying tls negotiation strategy for an http channel.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.453Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.HttpEndpointSpecClientTlsRenegotiation")
public enum HttpEndpointSpecClientTlsRenegotiation {
    /**
     * Never.
     */
    NEVER,
    /**
     * OnceAsClient.
     */
    ONCE_AS_CLIENT,
    /**
     * FreelyAsClient.
     */
    FREELY_AS_CLIENT,
}
