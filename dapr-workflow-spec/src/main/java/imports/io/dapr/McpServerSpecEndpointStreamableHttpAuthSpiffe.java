package imports.io.dapr;

/**
 * SPIFFE configures workload identity JWT injection via Dapr's Sentry CA.
 * <p>
 * No secret is required; Sentry issues the SVID automatically.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.461Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.McpServerSpecEndpointStreamableHttpAuthSpiffe")
@software.amazon.jsii.Jsii.Proxy(McpServerSpecEndpointStreamableHttpAuthSpiffe.Jsii$Proxy.class)
public interface McpServerSpecEndpointStreamableHttpAuthSpiffe extends software.amazon.jsii.JsiiSerializable {

    /**
     * JWT configures how the SPIFFE SVID JWT is attached to outbound requests.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthSpiffeJwt getJwt() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link McpServerSpecEndpointStreamableHttpAuthSpiffe}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link McpServerSpecEndpointStreamableHttpAuthSpiffe}
     */
    public static final class Builder implements software.amazon.jsii.Builder<McpServerSpecEndpointStreamableHttpAuthSpiffe> {
        imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthSpiffeJwt jwt;

        /**
         * Sets the value of {@link McpServerSpecEndpointStreamableHttpAuthSpiffe#getJwt}
         * @param jwt JWT configures how the SPIFFE SVID JWT is attached to outbound requests.
         * @return {@code this}
         */
        public Builder jwt(imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthSpiffeJwt jwt) {
            this.jwt = jwt;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link McpServerSpecEndpointStreamableHttpAuthSpiffe}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public McpServerSpecEndpointStreamableHttpAuthSpiffe build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link McpServerSpecEndpointStreamableHttpAuthSpiffe}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements McpServerSpecEndpointStreamableHttpAuthSpiffe {
        private final imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthSpiffeJwt jwt;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.jwt = software.amazon.jsii.Kernel.get(this, "jwt", software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthSpiffeJwt.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.jwt = builder.jwt;
        }

        @Override
        public final imports.io.dapr.McpServerSpecEndpointStreamableHttpAuthSpiffeJwt getJwt() {
            return this.jwt;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getJwt() != null) {
                data.set("jwt", om.valueToTree(this.getJwt()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.McpServerSpecEndpointStreamableHttpAuthSpiffe"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            McpServerSpecEndpointStreamableHttpAuthSpiffe.Jsii$Proxy that = (McpServerSpecEndpointStreamableHttpAuthSpiffe.Jsii$Proxy) o;

            return this.jwt != null ? this.jwt.equals(that.jwt) : that.jwt == null;
        }

        @Override
        public final int hashCode() {
            int result = this.jwt != null ? this.jwt.hashCode() : 0;
            return result;
        }
    }
}
