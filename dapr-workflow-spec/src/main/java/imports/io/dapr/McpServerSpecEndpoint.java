package imports.io.dapr;

/**
 * Endpoint describes the transport and target of the MCP server.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.457Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.McpServerSpecEndpoint")
@software.amazon.jsii.Jsii.Proxy(McpServerSpecEndpoint.Jsii$Proxy.class)
public interface McpServerSpecEndpoint extends software.amazon.jsii.JsiiSerializable {

    /**
     * SSE holds configuration for the legacy SSE transport.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.McpServerSpecEndpointSse getSse() {
        return null;
    }

    /**
     * Stdio holds configuration for the stdio subprocess transport.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.McpServerSpecEndpointStdio getStdio() {
        return null;
    }

    /**
     * StreamableHTTP holds configuration for the streamable_http transport.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.McpServerSpecEndpointStreamableHttp getStreamableHttp() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link McpServerSpecEndpoint}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link McpServerSpecEndpoint}
     */
    public static final class Builder implements software.amazon.jsii.Builder<McpServerSpecEndpoint> {
        imports.io.dapr.McpServerSpecEndpointSse sse;
        imports.io.dapr.McpServerSpecEndpointStdio stdio;
        imports.io.dapr.McpServerSpecEndpointStreamableHttp streamableHttp;

        /**
         * Sets the value of {@link McpServerSpecEndpoint#getSse}
         * @param sse SSE holds configuration for the legacy SSE transport.
         * @return {@code this}
         */
        public Builder sse(imports.io.dapr.McpServerSpecEndpointSse sse) {
            this.sse = sse;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpoint#getStdio}
         * @param stdio Stdio holds configuration for the stdio subprocess transport.
         * @return {@code this}
         */
        public Builder stdio(imports.io.dapr.McpServerSpecEndpointStdio stdio) {
            this.stdio = stdio;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpoint#getStreamableHttp}
         * @param streamableHttp StreamableHTTP holds configuration for the streamable_http transport.
         * @return {@code this}
         */
        public Builder streamableHttp(imports.io.dapr.McpServerSpecEndpointStreamableHttp streamableHttp) {
            this.streamableHttp = streamableHttp;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link McpServerSpecEndpoint}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public McpServerSpecEndpoint build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link McpServerSpecEndpoint}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements McpServerSpecEndpoint {
        private final imports.io.dapr.McpServerSpecEndpointSse sse;
        private final imports.io.dapr.McpServerSpecEndpointStdio stdio;
        private final imports.io.dapr.McpServerSpecEndpointStreamableHttp streamableHttp;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.sse = software.amazon.jsii.Kernel.get(this, "sse", software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecEndpointSse.class));
            this.stdio = software.amazon.jsii.Kernel.get(this, "stdio", software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecEndpointStdio.class));
            this.streamableHttp = software.amazon.jsii.Kernel.get(this, "streamableHttp", software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecEndpointStreamableHttp.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.sse = builder.sse;
            this.stdio = builder.stdio;
            this.streamableHttp = builder.streamableHttp;
        }

        @Override
        public final imports.io.dapr.McpServerSpecEndpointSse getSse() {
            return this.sse;
        }

        @Override
        public final imports.io.dapr.McpServerSpecEndpointStdio getStdio() {
            return this.stdio;
        }

        @Override
        public final imports.io.dapr.McpServerSpecEndpointStreamableHttp getStreamableHttp() {
            return this.streamableHttp;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getSse() != null) {
                data.set("sse", om.valueToTree(this.getSse()));
            }
            if (this.getStdio() != null) {
                data.set("stdio", om.valueToTree(this.getStdio()));
            }
            if (this.getStreamableHttp() != null) {
                data.set("streamableHttp", om.valueToTree(this.getStreamableHttp()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.McpServerSpecEndpoint"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            McpServerSpecEndpoint.Jsii$Proxy that = (McpServerSpecEndpoint.Jsii$Proxy) o;

            if (this.sse != null ? !this.sse.equals(that.sse) : that.sse != null) return false;
            if (this.stdio != null ? !this.stdio.equals(that.stdio) : that.stdio != null) return false;
            return this.streamableHttp != null ? this.streamableHttp.equals(that.streamableHttp) : that.streamableHttp == null;
        }

        @Override
        public final int hashCode() {
            int result = this.sse != null ? this.sse.hashCode() : 0;
            result = 31 * result + (this.stdio != null ? this.stdio.hashCode() : 0);
            result = 31 * result + (this.streamableHttp != null ? this.streamableHttp.hashCode() : 0);
            return result;
        }
    }
}
