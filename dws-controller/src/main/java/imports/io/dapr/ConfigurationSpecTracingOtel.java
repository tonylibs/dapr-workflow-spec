package imports.io.dapr;

/**
 * OtelSpec defines Otel exporter configurations.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.449Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecTracingOtel")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecTracingOtel.Jsii$Proxy.class)
public interface ConfigurationSpecTracingOtel extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getEndpointAddress();

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.Boolean getIsSecure();

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getProtocol();

    /**
     * Headers to add to the OTLP trace exporter request.
     * <p>
     * Each header can contain plaintext values, reference secrets, or reference environment variables.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.ConfigurationSpecTracingOtelHeaders> getHeaders() {
        return null;
    }

    /**
     * Timeout for the OTLP trace exporter request.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getTimeout() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecTracingOtel}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecTracingOtel}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecTracingOtel> {
        java.lang.String endpointAddress;
        java.lang.Boolean isSecure;
        java.lang.String protocol;
        java.util.List<imports.io.dapr.ConfigurationSpecTracingOtelHeaders> headers;
        java.lang.String timeout;

        /**
         * Sets the value of {@link ConfigurationSpecTracingOtel#getEndpointAddress}
         * @param endpointAddress the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder endpointAddress(java.lang.String endpointAddress) {
            this.endpointAddress = endpointAddress;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTracingOtel#getIsSecure}
         * @param isSecure the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder isSecure(java.lang.Boolean isSecure) {
            this.isSecure = isSecure;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTracingOtel#getProtocol}
         * @param protocol the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder protocol(java.lang.String protocol) {
            this.protocol = protocol;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTracingOtel#getHeaders}
         * @param headers Headers to add to the OTLP trace exporter request.
         *                Each header can contain plaintext values, reference secrets, or reference environment variables.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder headers(java.util.List<? extends imports.io.dapr.ConfigurationSpecTracingOtelHeaders> headers) {
            this.headers = (java.util.List<imports.io.dapr.ConfigurationSpecTracingOtelHeaders>)headers;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTracingOtel#getTimeout}
         * @param timeout Timeout for the OTLP trace exporter request.
         * @return {@code this}
         */
        public Builder timeout(java.lang.String timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecTracingOtel}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecTracingOtel build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecTracingOtel}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecTracingOtel {
        private final java.lang.String endpointAddress;
        private final java.lang.Boolean isSecure;
        private final java.lang.String protocol;
        private final java.util.List<imports.io.dapr.ConfigurationSpecTracingOtelHeaders> headers;
        private final java.lang.String timeout;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.endpointAddress = software.amazon.jsii.Kernel.get(this, "endpointAddress", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.isSecure = software.amazon.jsii.Kernel.get(this, "isSecure", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.protocol = software.amazon.jsii.Kernel.get(this, "protocol", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.headers = software.amazon.jsii.Kernel.get(this, "headers", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecTracingOtelHeaders.class)));
            this.timeout = software.amazon.jsii.Kernel.get(this, "timeout", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.endpointAddress = java.util.Objects.requireNonNull(builder.endpointAddress, "endpointAddress is required");
            this.isSecure = java.util.Objects.requireNonNull(builder.isSecure, "isSecure is required");
            this.protocol = java.util.Objects.requireNonNull(builder.protocol, "protocol is required");
            this.headers = (java.util.List<imports.io.dapr.ConfigurationSpecTracingOtelHeaders>)builder.headers;
            this.timeout = builder.timeout;
        }

        @Override
        public final java.lang.String getEndpointAddress() {
            return this.endpointAddress;
        }

        @Override
        public final java.lang.Boolean getIsSecure() {
            return this.isSecure;
        }

        @Override
        public final java.lang.String getProtocol() {
            return this.protocol;
        }

        @Override
        public final java.util.List<imports.io.dapr.ConfigurationSpecTracingOtelHeaders> getHeaders() {
            return this.headers;
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

            data.set("endpointAddress", om.valueToTree(this.getEndpointAddress()));
            data.set("isSecure", om.valueToTree(this.getIsSecure()));
            data.set("protocol", om.valueToTree(this.getProtocol()));
            if (this.getHeaders() != null) {
                data.set("headers", om.valueToTree(this.getHeaders()));
            }
            if (this.getTimeout() != null) {
                data.set("timeout", om.valueToTree(this.getTimeout()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecTracingOtel"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecTracingOtel.Jsii$Proxy that = (ConfigurationSpecTracingOtel.Jsii$Proxy) o;

            if (!endpointAddress.equals(that.endpointAddress)) return false;
            if (!isSecure.equals(that.isSecure)) return false;
            if (!protocol.equals(that.protocol)) return false;
            if (this.headers != null ? !this.headers.equals(that.headers) : that.headers != null) return false;
            return this.timeout != null ? this.timeout.equals(that.timeout) : that.timeout == null;
        }

        @Override
        public final int hashCode() {
            int result = this.endpointAddress.hashCode();
            result = 31 * result + (this.isSecure.hashCode());
            result = 31 * result + (this.protocol.hashCode());
            result = 31 * result + (this.headers != null ? this.headers.hashCode() : 0);
            result = 31 * result + (this.timeout != null ? this.timeout.hashCode() : 0);
            return result;
        }
    }
}
