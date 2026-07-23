package imports.io.dapr;

/**
 * OAuth2 configures OAuth2 client credentials flow for the MCP server.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.458Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.McpServerSpecEndpointSseAuthOauth2")
@software.amazon.jsii.Jsii.Proxy(McpServerSpecEndpointSseAuthOauth2.Jsii$Proxy.class)
public interface McpServerSpecEndpointSseAuthOauth2 extends software.amazon.jsii.JsiiSerializable {

    /**
     * Issuer is the token endpoint of the authorization server.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getIssuer();

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getAudience() {
        return null;
    }

    /**
     * ClientID is the OAuth2 client identifier sent to the token endpoint.
     * <p>
     * Required by RFC 6749 for the standard client_credentials flow (form parameter
     * or HTTP Basic username). May be left empty for non-standard flows (e.g. JWT-bearer
     * assertions or token endpoints that key solely on the client_secret). Client IDs
     * are typically public identifiers; use spec.headers with a secretKeyRef if your
     * environment requires sourcing the value from a secret store.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getClientId() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getScopes() {
        return null;
    }

    /**
     * SecretKeyRef references the client secret in the configured secret store.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.McpServerSpecEndpointSseAuthOauth2SecretKeyRef getSecretKeyRef() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link McpServerSpecEndpointSseAuthOauth2}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link McpServerSpecEndpointSseAuthOauth2}
     */
    public static final class Builder implements software.amazon.jsii.Builder<McpServerSpecEndpointSseAuthOauth2> {
        java.lang.String issuer;
        java.lang.String audience;
        java.lang.String clientId;
        java.util.List<java.lang.String> scopes;
        imports.io.dapr.McpServerSpecEndpointSseAuthOauth2SecretKeyRef secretKeyRef;

        /**
         * Sets the value of {@link McpServerSpecEndpointSseAuthOauth2#getIssuer}
         * @param issuer Issuer is the token endpoint of the authorization server. This parameter is required.
         * @return {@code this}
         */
        public Builder issuer(java.lang.String issuer) {
            this.issuer = issuer;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpointSseAuthOauth2#getAudience}
         * @param audience the value to be set.
         * @return {@code this}
         */
        public Builder audience(java.lang.String audience) {
            this.audience = audience;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpointSseAuthOauth2#getClientId}
         * @param clientId ClientID is the OAuth2 client identifier sent to the token endpoint.
         *                 Required by RFC 6749 for the standard client_credentials flow (form parameter
         *                 or HTTP Basic username). May be left empty for non-standard flows (e.g. JWT-bearer
         *                 assertions or token endpoints that key solely on the client_secret). Client IDs
         *                 are typically public identifiers; use spec.headers with a secretKeyRef if your
         *                 environment requires sourcing the value from a secret store.
         * @return {@code this}
         */
        public Builder clientId(java.lang.String clientId) {
            this.clientId = clientId;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpointSseAuthOauth2#getScopes}
         * @param scopes the value to be set.
         * @return {@code this}
         */
        public Builder scopes(java.util.List<java.lang.String> scopes) {
            this.scopes = scopes;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpointSseAuthOauth2#getSecretKeyRef}
         * @param secretKeyRef SecretKeyRef references the client secret in the configured secret store.
         * @return {@code this}
         */
        public Builder secretKeyRef(imports.io.dapr.McpServerSpecEndpointSseAuthOauth2SecretKeyRef secretKeyRef) {
            this.secretKeyRef = secretKeyRef;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link McpServerSpecEndpointSseAuthOauth2}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public McpServerSpecEndpointSseAuthOauth2 build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link McpServerSpecEndpointSseAuthOauth2}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements McpServerSpecEndpointSseAuthOauth2 {
        private final java.lang.String issuer;
        private final java.lang.String audience;
        private final java.lang.String clientId;
        private final java.util.List<java.lang.String> scopes;
        private final imports.io.dapr.McpServerSpecEndpointSseAuthOauth2SecretKeyRef secretKeyRef;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.issuer = software.amazon.jsii.Kernel.get(this, "issuer", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.audience = software.amazon.jsii.Kernel.get(this, "audience", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.clientId = software.amazon.jsii.Kernel.get(this, "clientId", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.scopes = software.amazon.jsii.Kernel.get(this, "scopes", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.secretKeyRef = software.amazon.jsii.Kernel.get(this, "secretKeyRef", software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecEndpointSseAuthOauth2SecretKeyRef.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.issuer = java.util.Objects.requireNonNull(builder.issuer, "issuer is required");
            this.audience = builder.audience;
            this.clientId = builder.clientId;
            this.scopes = builder.scopes;
            this.secretKeyRef = builder.secretKeyRef;
        }

        @Override
        public final java.lang.String getIssuer() {
            return this.issuer;
        }

        @Override
        public final java.lang.String getAudience() {
            return this.audience;
        }

        @Override
        public final java.lang.String getClientId() {
            return this.clientId;
        }

        @Override
        public final java.util.List<java.lang.String> getScopes() {
            return this.scopes;
        }

        @Override
        public final imports.io.dapr.McpServerSpecEndpointSseAuthOauth2SecretKeyRef getSecretKeyRef() {
            return this.secretKeyRef;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("issuer", om.valueToTree(this.getIssuer()));
            if (this.getAudience() != null) {
                data.set("audience", om.valueToTree(this.getAudience()));
            }
            if (this.getClientId() != null) {
                data.set("clientId", om.valueToTree(this.getClientId()));
            }
            if (this.getScopes() != null) {
                data.set("scopes", om.valueToTree(this.getScopes()));
            }
            if (this.getSecretKeyRef() != null) {
                data.set("secretKeyRef", om.valueToTree(this.getSecretKeyRef()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.McpServerSpecEndpointSseAuthOauth2"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            McpServerSpecEndpointSseAuthOauth2.Jsii$Proxy that = (McpServerSpecEndpointSseAuthOauth2.Jsii$Proxy) o;

            if (!issuer.equals(that.issuer)) return false;
            if (this.audience != null ? !this.audience.equals(that.audience) : that.audience != null) return false;
            if (this.clientId != null ? !this.clientId.equals(that.clientId) : that.clientId != null) return false;
            if (this.scopes != null ? !this.scopes.equals(that.scopes) : that.scopes != null) return false;
            return this.secretKeyRef != null ? this.secretKeyRef.equals(that.secretKeyRef) : that.secretKeyRef == null;
        }

        @Override
        public final int hashCode() {
            int result = this.issuer.hashCode();
            result = 31 * result + (this.audience != null ? this.audience.hashCode() : 0);
            result = 31 * result + (this.clientId != null ? this.clientId.hashCode() : 0);
            result = 31 * result + (this.scopes != null ? this.scopes.hashCode() : 0);
            result = 31 * result + (this.secretKeyRef != null ? this.secretKeyRef.hashCode() : 0);
            return result;
        }
    }
}
