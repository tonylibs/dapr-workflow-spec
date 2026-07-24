package imports.io.dapr;

/**
 * ZipkinSpec defines Zipkin trace configurations.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.450Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecTracingZipkin")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecTracingZipkin.Jsii$Proxy.class)
public interface ConfigurationSpecTracingZipkin extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getEndpointAddress();

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecTracingZipkin}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecTracingZipkin}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecTracingZipkin> {
        java.lang.String endpointAddress;

        /**
         * Sets the value of {@link ConfigurationSpecTracingZipkin#getEndpointAddress}
         * @param endpointAddress the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder endpointAddress(java.lang.String endpointAddress) {
            this.endpointAddress = endpointAddress;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecTracingZipkin}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecTracingZipkin build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecTracingZipkin}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecTracingZipkin {
        private final java.lang.String endpointAddress;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.endpointAddress = software.amazon.jsii.Kernel.get(this, "endpointAddress", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.endpointAddress = java.util.Objects.requireNonNull(builder.endpointAddress, "endpointAddress is required");
        }

        @Override
        public final java.lang.String getEndpointAddress() {
            return this.endpointAddress;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("endpointAddress", om.valueToTree(this.getEndpointAddress()));

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecTracingZipkin"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecTracingZipkin.Jsii$Proxy that = (ConfigurationSpecTracingZipkin.Jsii$Proxy) o;

            return this.endpointAddress.equals(that.endpointAddress);
        }

        @Override
        public final int hashCode() {
            int result = this.endpointAddress.hashCode();
            return result;
        }
    }
}
