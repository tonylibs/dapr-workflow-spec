package imports.io.dapr;

/**
 * Auth configures authentication for the MCP server connection.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.461Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.McpServerSpecEndpointStreamableHttpAuth")
@software.amazon.jsii.Jsii.Proxy(McpServerSpecEndpointStreamableHttpAuth.Jsii$Proxy.class)
public interface McpServerSpecEndpointStreamableHttpAuth extends software.amazon.jsii.JsiiSerializable {

    /**
     * OAuth2 configures OAuth2 client credentials flow for the MCP server.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthOauth2 getOauth2() {
        return null;
    }

    /**
     * SecretStore names the Dapr secret store used to resolve secretKeyRef entries in spec.endpoint.http.headers. auth.oauth2.secretKeyRef is resolved separately at call time by the MCP worker. Defaults to "kubernetes".
     * <p>
     * Default: kubernetes".
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getSecretStore() {
        return null;
    }

    /**
     * SPIFFE configures workload identity JWT injection via Dapr's Sentry CA.
     * <p>
     * No secret is required; Sentry issues the SVID automatically.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthSpiffe getSpiffe() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link McpServerSpecEndpointStreamableHttpAuth}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link McpServerSpecEndpointStreamableHttpAuth}
     */
    public static final class Builder implements software.amazon.jsii.Builder<McpServerSpecEndpointStreamableHttpAuth> {
        imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthOauth2 oauth2;
        java.lang.String secretStore;
        imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthSpiffe spiffe;

        /**
         * Sets the value of {@link McpServerSpecEndpointStreamableHttpAuth#getOauth2}
         * @param oauth2 OAuth2 configures OAuth2 client credentials flow for the MCP server.
         * @return {@code this}
         */
        public Builder oauth2(imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthOauth2 oauth2) {
            this.oauth2 = oauth2;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpointStreamableHttpAuth#getSecretStore}
         * @param secretStore SecretStore names the Dapr secret store used to resolve secretKeyRef entries in spec.endpoint.http.headers. auth.oauth2.secretKeyRef is resolved separately at call time by the MCP worker. Defaults to "kubernetes".
         * @return {@code this}
         */
        public Builder secretStore(java.lang.String secretStore) {
            this.secretStore = secretStore;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpointStreamableHttpAuth#getSpiffe}
         * @param spiffe SPIFFE configures workload identity JWT injection via Dapr's Sentry CA.
         *               No secret is required; Sentry issues the SVID automatically.
         * @return {@code this}
         */
        public Builder spiffe(imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthSpiffe spiffe) {
            this.spiffe = spiffe;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link McpServerSpecEndpointStreamableHttpAuth}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public McpServerSpecEndpointStreamableHttpAuth build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link McpServerSpecEndpointStreamableHttpAuth}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements McpServerSpecEndpointStreamableHttpAuth {
        private final imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthOauth2 oauth2;
        private final java.lang.String secretStore;
        private final imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthSpiffe spiffe;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.oauth2 = software.amazon.jsii.Kernel.get(this, "oauth2", software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthOauth2.class));
            this.secretStore = software.amazon.jsii.Kernel.get(this, "secretStore", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.spiffe = software.amazon.jsii.Kernel.get(this, "spiffe", software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthSpiffe.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.oauth2 = builder.oauth2;
            this.secretStore = builder.secretStore;
            this.spiffe = builder.spiffe;
        }

        @Override
        public final imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthOauth2 getOauth2() {
            return this.oauth2;
        }

        @Override
        public final java.lang.String getSecretStore() {
            return this.secretStore;
        }

        @Override
        public final imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthSpiffe getSpiffe() {
            return this.spiffe;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getOauth2() != null) {
                data.set("oauth2", om.valueToTree(this.getOauth2()));
            }
            if (this.getSecretStore() != null) {
                data.set("secretStore", om.valueToTree(this.getSecretStore()));
            }
            if (this.getSpiffe() != null) {
                data.set("spiffe", om.valueToTree(this.getSpiffe()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.McpServerSpecEndpointStreamableHttpAuth"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            McpServerSpecEndpointStreamableHttpAuth.Jsii$Proxy that = (McpServerSpecEndpointStreamableHttpAuth.Jsii$Proxy) o;

            if (this.oauth2 != null ? !this.oauth2.equals(that.oauth2) : that.oauth2 != null) return false;
            if (this.secretStore != null ? !this.secretStore.equals(that.secretStore) : that.secretStore != null) return false;
            return this.spiffe != null ? this.spiffe.equals(that.spiffe) : that.spiffe == null;
        }

        @Override
        public final int hashCode() {
            int result = this.oauth2 != null ? this.oauth2.hashCode() : 0;
            result = 31 * result + (this.secretStore != null ? this.secretStore.hashCode() : 0);
            result = 31 * result + (this.spiffe != null ? this.spiffe.hashCode() : 0);
            return result;
        }
    }
}
