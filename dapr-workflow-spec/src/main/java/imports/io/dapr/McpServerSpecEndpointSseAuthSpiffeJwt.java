package imports.io.dapr;

/**
 * JWT configures how the SPIFFE SVID JWT is attached to outbound requests.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.459Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.McpServerSpecEndpointSseAuthSpiffeJwt")
@software.amazon.jsii.Jsii.Proxy(McpServerSpecEndpointSseAuthSpiffeJwt.Jsii$Proxy.class)
public interface McpServerSpecEndpointSseAuthSpiffeJwt extends software.amazon.jsii.JsiiSerializable {

    /**
     * Audience is the intended audience for the JWT (e.g. "mcp://payments").
     */
    @org.jetbrains.annotations.NotNull java.lang.String getAudience();

    /**
     * Header is the HTTP header name to inject the JWT into (e.g. "Authorization").
     */
    @org.jetbrains.annotations.NotNull java.lang.String getHeader();

    /**
     * HeaderValuePrefix is an optional string prepended to the JWT value (e.g. "Bearer ").
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getHeaderValuePrefix() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link McpServerSpecEndpointSseAuthSpiffeJwt}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link McpServerSpecEndpointSseAuthSpiffeJwt}
     */
    public static final class Builder implements software.amazon.jsii.Builder<McpServerSpecEndpointSseAuthSpiffeJwt> {
        java.lang.String audience;
        java.lang.String header;
        java.lang.String headerValuePrefix;

        /**
         * Sets the value of {@link McpServerSpecEndpointSseAuthSpiffeJwt#getAudience}
         * @param audience Audience is the intended audience for the JWT (e.g. "mcp://payments"). This parameter is required.
         * @return {@code this}
         */
        public Builder audience(java.lang.String audience) {
            this.audience = audience;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpointSseAuthSpiffeJwt#getHeader}
         * @param header Header is the HTTP header name to inject the JWT into (e.g. "Authorization"). This parameter is required.
         * @return {@code this}
         */
        public Builder header(java.lang.String header) {
            this.header = header;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpointSseAuthSpiffeJwt#getHeaderValuePrefix}
         * @param headerValuePrefix HeaderValuePrefix is an optional string prepended to the JWT value (e.g. "Bearer ").
         * @return {@code this}
         */
        public Builder headerValuePrefix(java.lang.String headerValuePrefix) {
            this.headerValuePrefix = headerValuePrefix;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link McpServerSpecEndpointSseAuthSpiffeJwt}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public McpServerSpecEndpointSseAuthSpiffeJwt build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link McpServerSpecEndpointSseAuthSpiffeJwt}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements McpServerSpecEndpointSseAuthSpiffeJwt {
        private final java.lang.String audience;
        private final java.lang.String header;
        private final java.lang.String headerValuePrefix;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.audience = software.amazon.jsii.Kernel.get(this, "audience", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.header = software.amazon.jsii.Kernel.get(this, "header", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.headerValuePrefix = software.amazon.jsii.Kernel.get(this, "headerValuePrefix", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.audience = java.util.Objects.requireNonNull(builder.audience, "audience is required");
            this.header = java.util.Objects.requireNonNull(builder.header, "header is required");
            this.headerValuePrefix = builder.headerValuePrefix;
        }

        @Override
        public final java.lang.String getAudience() {
            return this.audience;
        }

        @Override
        public final java.lang.String getHeader() {
            return this.header;
        }

        @Override
        public final java.lang.String getHeaderValuePrefix() {
            return this.headerValuePrefix;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("audience", om.valueToTree(this.getAudience()));
            data.set("header", om.valueToTree(this.getHeader()));
            if (this.getHeaderValuePrefix() != null) {
                data.set("headerValuePrefix", om.valueToTree(this.getHeaderValuePrefix()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.McpServerSpecEndpointSseAuthSpiffeJwt"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            McpServerSpecEndpointSseAuthSpiffeJwt.Jsii$Proxy that = (McpServerSpecEndpointSseAuthSpiffeJwt.Jsii$Proxy) o;

            if (!audience.equals(that.audience)) return false;
            if (!header.equals(that.header)) return false;
            return this.headerValuePrefix != null ? this.headerValuePrefix.equals(that.headerValuePrefix) : that.headerValuePrefix == null;
        }

        @Override
        public final int hashCode() {
            int result = this.audience.hashCode();
            result = 31 * result + (this.header.hashCode());
            result = 31 * result + (this.headerValuePrefix != null ? this.headerValuePrefix.hashCode() : 0);
            return result;
        }
    }
}
