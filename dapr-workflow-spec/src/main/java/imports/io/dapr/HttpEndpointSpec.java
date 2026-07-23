package imports.io.dapr;

/**
 * HTTPEndpointSpec describes an access specification for allowing external service invocations.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.452Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.HttpEndpointSpec")
@software.amazon.jsii.Jsii.Proxy(HttpEndpointSpec.Jsii$Proxy.class)
public interface HttpEndpointSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getBaseUrl();

    /**
     * TLS describes how to build client or server TLS configurations.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.HttpEndpointSpecClientTls getClientTls() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.HttpEndpointSpecHeaders> getHeaders() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link HttpEndpointSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link HttpEndpointSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<HttpEndpointSpec> {
        java.lang.String baseUrl;
        imports.io.dapr.HttpEndpointSpecClientTls clientTls;
        java.util.List<imports.io.dapr.HttpEndpointSpecHeaders> headers;

        /**
         * Sets the value of {@link HttpEndpointSpec#getBaseUrl}
         * @param baseUrl the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder baseUrl(java.lang.String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the value of {@link HttpEndpointSpec#getClientTls}
         * @param clientTls TLS describes how to build client or server TLS configurations.
         * @return {@code this}
         */
        public Builder clientTls(imports.io.dapr.HttpEndpointSpecClientTls clientTls) {
            this.clientTls = clientTls;
            return this;
        }

        /**
         * Sets the value of {@link HttpEndpointSpec#getHeaders}
         * @param headers the value to be set.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder headers(java.util.List<? extends imports.io.dapr.HttpEndpointSpecHeaders> headers) {
            this.headers = (java.util.List<imports.io.dapr.HttpEndpointSpecHeaders>)headers;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link HttpEndpointSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public HttpEndpointSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link HttpEndpointSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements HttpEndpointSpec {
        private final java.lang.String baseUrl;
        private final imports.io.dapr.HttpEndpointSpecClientTls clientTls;
        private final java.util.List<imports.io.dapr.HttpEndpointSpecHeaders> headers;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.baseUrl = software.amazon.jsii.Kernel.get(this, "baseUrl", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.clientTls = software.amazon.jsii.Kernel.get(this, "clientTls", software.amazon.jsii.NativeType.forClass(imports.io.dapr.HttpEndpointSpecClientTls.class));
            this.headers = software.amazon.jsii.Kernel.get(this, "headers", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.HttpEndpointSpecHeaders.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.baseUrl = java.util.Objects.requireNonNull(builder.baseUrl, "baseUrl is required");
            this.clientTls = builder.clientTls;
            this.headers = (java.util.List<imports.io.dapr.HttpEndpointSpecHeaders>)builder.headers;
        }

        @Override
        public final java.lang.String getBaseUrl() {
            return this.baseUrl;
        }

        @Override
        public final imports.io.dapr.HttpEndpointSpecClientTls getClientTls() {
            return this.clientTls;
        }

        @Override
        public final java.util.List<imports.io.dapr.HttpEndpointSpecHeaders> getHeaders() {
            return this.headers;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("baseUrl", om.valueToTree(this.getBaseUrl()));
            if (this.getClientTls() != null) {
                data.set("clientTls", om.valueToTree(this.getClientTls()));
            }
            if (this.getHeaders() != null) {
                data.set("headers", om.valueToTree(this.getHeaders()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.HttpEndpointSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            HttpEndpointSpec.Jsii$Proxy that = (HttpEndpointSpec.Jsii$Proxy) o;

            if (!baseUrl.equals(that.baseUrl)) return false;
            if (this.clientTls != null ? !this.clientTls.equals(that.clientTls) : that.clientTls != null) return false;
            return this.headers != null ? this.headers.equals(that.headers) : that.headers == null;
        }

        @Override
        public final int hashCode() {
            int result = this.baseUrl.hashCode();
            result = 31 * result + (this.clientTls != null ? this.clientTls.hashCode() : 0);
            result = 31 * result + (this.headers != null ? this.headers.hashCode() : 0);
            return result;
        }
    }
}
