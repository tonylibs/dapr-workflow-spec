package imports.io.dapr;

/**
 * SSE holds configuration for the legacy SSE transport.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.458Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.McpServerSpecEndpointSse")
@software.amazon.jsii.Jsii.Proxy(McpServerSpecEndpointSse.Jsii$Proxy.class)
public interface McpServerSpecEndpointSse extends software.amazon.jsii.JsiiSerializable {

    /**
     * URL is the endpoint URL of the MCP server.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getUrl();

    /**
     * Auth configures authentication for the MCP server connection.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.McpServerSpecEndpointSseAuth getAuth() {
        return null;
    }

    /**
     * Headers are injected on all outbound HTTP requests to the MCP server.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.McpServerSpecEndpointSseHeaders> getHeaders() {
        return null;
    }

    /**
     * ProtocolVersion pins the MCP spec version the server implements, using the date-based format defined by the MCP specification (e.g. "2025-06-18"). When unset, the go-sdk negotiates the latest version supported by both sides.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getProtocolVersion() {
        return null;
    }

    /**
     * Timeout is the per-call deadline for MCP requests.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getTimeout() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link McpServerSpecEndpointSse}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link McpServerSpecEndpointSse}
     */
    public static final class Builder implements software.amazon.jsii.Builder<McpServerSpecEndpointSse> {
        java.lang.String url;
        imports.io.dapr.McpServerSpecEndpointSseAuth auth;
        java.util.List<imports.io.dapr.McpServerSpecEndpointSseHeaders> headers;
        java.lang.String protocolVersion;
        java.lang.String timeout;

        /**
         * Sets the value of {@link McpServerSpecEndpointSse#getUrl}
         * @param url URL is the endpoint URL of the MCP server. This parameter is required.
         * @return {@code this}
         */
        public Builder url(java.lang.String url) {
            this.url = url;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpointSse#getAuth}
         * @param auth Auth configures authentication for the MCP server connection.
         * @return {@code this}
         */
        public Builder auth(imports.io.dapr.McpServerSpecEndpointSseAuth auth) {
            this.auth = auth;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpointSse#getHeaders}
         * @param headers Headers are injected on all outbound HTTP requests to the MCP server.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder headers(java.util.List<? extends imports.io.dapr.McpServerSpecEndpointSseHeaders> headers) {
            this.headers = (java.util.List<imports.io.dapr.McpServerSpecEndpointSseHeaders>)headers;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpointSse#getProtocolVersion}
         * @param protocolVersion ProtocolVersion pins the MCP spec version the server implements, using the date-based format defined by the MCP specification (e.g. "2025-06-18"). When unset, the go-sdk negotiates the latest version supported by both sides.
         * @return {@code this}
         */
        public Builder protocolVersion(java.lang.String protocolVersion) {
            this.protocolVersion = protocolVersion;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpointSse#getTimeout}
         * @param timeout Timeout is the per-call deadline for MCP requests.
         * @return {@code this}
         */
        public Builder timeout(java.lang.String timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link McpServerSpecEndpointSse}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public McpServerSpecEndpointSse build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link McpServerSpecEndpointSse}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements McpServerSpecEndpointSse {
        private final java.lang.String url;
        private final imports.io.dapr.McpServerSpecEndpointSseAuth auth;
        private final java.util.List<imports.io.dapr.McpServerSpecEndpointSseHeaders> headers;
        private final java.lang.String protocolVersion;
        private final java.lang.String timeout;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.url = software.amazon.jsii.Kernel.get(this, "url", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.auth = software.amazon.jsii.Kernel.get(this, "auth", software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecEndpointSseAuth.class));
            this.headers = software.amazon.jsii.Kernel.get(this, "headers", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecEndpointSseHeaders.class)));
            this.protocolVersion = software.amazon.jsii.Kernel.get(this, "protocolVersion", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.timeout = software.amazon.jsii.Kernel.get(this, "timeout", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.url = java.util.Objects.requireNonNull(builder.url, "url is required");
            this.auth = builder.auth;
            this.headers = (java.util.List<imports.io.dapr.McpServerSpecEndpointSseHeaders>)builder.headers;
            this.protocolVersion = builder.protocolVersion;
            this.timeout = builder.timeout;
        }

        @Override
        public final java.lang.String getUrl() {
            return this.url;
        }

        @Override
        public final imports.io.dapr.McpServerSpecEndpointSseAuth getAuth() {
            return this.auth;
        }

        @Override
        public final java.util.List<imports.io.dapr.McpServerSpecEndpointSseHeaders> getHeaders() {
            return this.headers;
        }

        @Override
        public final java.lang.String getProtocolVersion() {
            return this.protocolVersion;
        }

        @Override
        public final java.lang.String getTimeout() {
            return this.timeout;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("url", om.valueToTree(this.getUrl()));
            if (this.getAuth() != null) {
                data.set("auth", om.valueToTree(this.getAuth()));
            }
            if (this.getHeaders() != null) {
                data.set("headers", om.valueToTree(this.getHeaders()));
            }
            if (this.getProtocolVersion() != null) {
                data.set("protocolVersion", om.valueToTree(this.getProtocolVersion()));
            }
            if (this.getTimeout() != null) {
                data.set("timeout", om.valueToTree(this.getTimeout()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.McpServerSpecEndpointSse"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            McpServerSpecEndpointSse.Jsii$Proxy that = (McpServerSpecEndpointSse.Jsii$Proxy) o;

            if (!url.equals(that.url)) return false;
            if (this.auth != null ? !this.auth.equals(that.auth) : that.auth != null) return false;
            if (this.headers != null ? !this.headers.equals(that.headers) : that.headers != null) return false;
            if (this.protocolVersion != null ? !this.protocolVersion.equals(that.protocolVersion) : that.protocolVersion != null) return false;
            return this.timeout != null ? this.timeout.equals(that.timeout) : that.timeout == null;
        }

        @Override
        public final int hashCode() {
            int result = this.url.hashCode();
            result = 31 * result + (this.auth != null ? this.auth.hashCode() : 0);
            result = 31 * result + (this.headers != null ? this.headers.hashCode() : 0);
            result = 31 * result + (this.protocolVersion != null ? this.protocolVersion.hashCode() : 0);
            result = 31 * result + (this.timeout != null ? this.timeout.hashCode() : 0);
            return result;
        }
    }
}
