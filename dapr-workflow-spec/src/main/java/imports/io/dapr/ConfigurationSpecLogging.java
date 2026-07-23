package imports.io.dapr;

/**
 * LoggingSpec defines the configuration for logging.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.440Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecLogging")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecLogging.Jsii$Proxy.class)
public interface ConfigurationSpecLogging extends software.amazon.jsii.JsiiSerializable {

    /**
     * Configure API logging.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecLoggingApiLogging getApiLogging() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecLogging}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecLogging}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecLogging> {
        imports.io.dapr.ConfigurationSpecLoggingApiLogging apiLogging;

        /**
         * Sets the value of {@link ConfigurationSpecLogging#getApiLogging}
         * @param apiLogging Configure API logging.
         * @return {@code this}
         */
        public Builder apiLogging(imports.io.dapr.ConfigurationSpecLoggingApiLogging apiLogging) {
            this.apiLogging = apiLogging;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecLogging}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecLogging build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecLogging}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecLogging {
        private final imports.io.dapr.ConfigurationSpecLoggingApiLogging apiLogging;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.apiLogging = software.amazon.jsii.Kernel.get(this, "apiLogging", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecLoggingApiLogging.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.apiLogging = builder.apiLogging;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecLoggingApiLogging getApiLogging() {
            return this.apiLogging;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getApiLogging() != null) {
                data.set("apiLogging", om.valueToTree(this.getApiLogging()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecLogging"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecLogging.Jsii$Proxy that = (ConfigurationSpecLogging.Jsii$Proxy) o;

            return this.apiLogging != null ? this.apiLogging.equals(that.apiLogging) : that.apiLogging == null;
        }

        @Override
        public final int hashCode() {
            int result = this.apiLogging != null ? this.apiLogging.hashCode() : 0;
            return result;
        }
    }
}
