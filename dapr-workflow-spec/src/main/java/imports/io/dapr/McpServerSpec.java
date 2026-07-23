package imports.io.dapr;

/**
 * MCPServerSpec is the full configuration for an MCP server connection.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.455Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.McpServerSpec")
@software.amazon.jsii.Jsii.Proxy(McpServerSpec.Jsii$Proxy.class)
public interface McpServerSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     * Endpoint describes the transport and target of the MCP server.
     */
    @org.jetbrains.annotations.NotNull imports.io.dapr.McpServerSpecEndpoint getEndpoint();

    /**
     * Catalog holds user-facing governance metadata (display name, description, owner, tags, links).
     * <p>
     * It is purely informational and not used at runtime.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.McpServerSpecCatalog getCatalog() {
        return null;
    }

    /**
     * IgnoreErrors, when true, allows daprd to continue running if validation or secret resolution fails for this MCPServer.
     * <p>
     * When false (default),
     * such failures cause daprd to exit gracefully.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getIgnoreErrors() {
        return null;
    }

    /**
     * Middleware defines optional workflow hooks invoked around each tool call.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.McpServerSpecMiddleware getMiddleware() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link McpServerSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link McpServerSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<McpServerSpec> {
        imports.io.dapr.McpServerSpecEndpoint endpoint;
        imports.io.dapr.McpServerSpecCatalog catalog;
        java.lang.Boolean ignoreErrors;
        imports.io.dapr.McpServerSpecMiddleware middleware;

        /**
         * Sets the value of {@link McpServerSpec#getEndpoint}
         * @param endpoint Endpoint describes the transport and target of the MCP server. This parameter is required.
         * @return {@code this}
         */
        public Builder endpoint(imports.io.dapr.McpServerSpecEndpoint endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpec#getCatalog}
         * @param catalog Catalog holds user-facing governance metadata (display name, description, owner, tags, links).
         *                It is purely informational and not used at runtime.
         * @return {@code this}
         */
        public Builder catalog(imports.io.dapr.McpServerSpecCatalog catalog) {
            this.catalog = catalog;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpec#getIgnoreErrors}
         * @param ignoreErrors IgnoreErrors, when true, allows daprd to continue running if validation or secret resolution fails for this MCPServer.
         *                     When false (default),
         *                     such failures cause daprd to exit gracefully.
         * @return {@code this}
         */
        public Builder ignoreErrors(java.lang.Boolean ignoreErrors) {
            this.ignoreErrors = ignoreErrors;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpec#getMiddleware}
         * @param middleware Middleware defines optional workflow hooks invoked around each tool call.
         * @return {@code this}
         */
        public Builder middleware(imports.io.dapr.McpServerSpecMiddleware middleware) {
            this.middleware = middleware;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link McpServerSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public McpServerSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link McpServerSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements McpServerSpec {
        private final imports.io.dapr.McpServerSpecEndpoint endpoint;
        private final imports.io.dapr.McpServerSpecCatalog catalog;
        private final java.lang.Boolean ignoreErrors;
        private final imports.io.dapr.McpServerSpecMiddleware middleware;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.endpoint = software.amazon.jsii.Kernel.get(this, "endpoint", software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecEndpoint.class));
            this.catalog = software.amazon.jsii.Kernel.get(this, "catalog", software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecCatalog.class));
            this.ignoreErrors = software.amazon.jsii.Kernel.get(this, "ignoreErrors", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.middleware = software.amazon.jsii.Kernel.get(this, "middleware", software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecMiddleware.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.endpoint = java.util.Objects.requireNonNull(builder.endpoint, "endpoint is required");
            this.catalog = builder.catalog;
            this.ignoreErrors = builder.ignoreErrors;
            this.middleware = builder.middleware;
        }

        @Override
        public final imports.io.dapr.McpServerSpecEndpoint getEndpoint() {
            return this.endpoint;
        }

        @Override
        public final imports.io.dapr.McpServerSpecCatalog getCatalog() {
            return this.catalog;
        }

        @Override
        public final java.lang.Boolean getIgnoreErrors() {
            return this.ignoreErrors;
        }

        @Override
        public final imports.io.dapr.McpServerSpecMiddleware getMiddleware() {
            return this.middleware;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("endpoint", om.valueToTree(this.getEndpoint()));
            if (this.getCatalog() != null) {
                data.set("catalog", om.valueToTree(this.getCatalog()));
            }
            if (this.getIgnoreErrors() != null) {
                data.set("ignoreErrors", om.valueToTree(this.getIgnoreErrors()));
            }
            if (this.getMiddleware() != null) {
                data.set("middleware", om.valueToTree(this.getMiddleware()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.McpServerSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            McpServerSpec.Jsii$Proxy that = (McpServerSpec.Jsii$Proxy) o;

            if (!endpoint.equals(that.endpoint)) return false;
            if (this.catalog != null ? !this.catalog.equals(that.catalog) : that.catalog != null) return false;
            if (this.ignoreErrors != null ? !this.ignoreErrors.equals(that.ignoreErrors) : that.ignoreErrors != null) return false;
            return this.middleware != null ? this.middleware.equals(that.middleware) : that.middleware == null;
        }

        @Override
        public final int hashCode() {
            int result = this.endpoint.hashCode();
            result = 31 * result + (this.catalog != null ? this.catalog.hashCode() : 0);
            result = 31 * result + (this.ignoreErrors != null ? this.ignoreErrors.hashCode() : 0);
            result = 31 * result + (this.middleware != null ? this.middleware.hashCode() : 0);
            return result;
        }
    }
}
