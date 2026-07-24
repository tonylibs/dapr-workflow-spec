package imports.io.dapr;

/**
 * WasmSpec describes the security profile for all Dapr Wasm components.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.450Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecWasm")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecWasm.Jsii$Proxy.class)
public interface ConfigurationSpecWasm extends software.amazon.jsii.JsiiSerializable {

    /**
     * Force enabling strict sandbox mode for all WASM components.
     * <p>
     * When this is enabled, WASM components always run in strict mode regardless of their configuration.
     * Strict mode enhances security of the WASM sandbox by limiting access to certain capabilities such as real-time clocks and random number generators.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getStrictSandbox() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecWasm}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecWasm}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecWasm> {
        java.lang.Boolean strictSandbox;

        /**
         * Sets the value of {@link ConfigurationSpecWasm#getStrictSandbox}
         * @param strictSandbox Force enabling strict sandbox mode for all WASM components.
         *                      When this is enabled, WASM components always run in strict mode regardless of their configuration.
         *                      Strict mode enhances security of the WASM sandbox by limiting access to certain capabilities such as real-time clocks and random number generators.
         * @return {@code this}
         */
        public Builder strictSandbox(java.lang.Boolean strictSandbox) {
            this.strictSandbox = strictSandbox;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecWasm}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecWasm build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecWasm}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecWasm {
        private final java.lang.Boolean strictSandbox;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.strictSandbox = software.amazon.jsii.Kernel.get(this, "strictSandbox", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.strictSandbox = builder.strictSandbox;
        }

        @Override
        public final java.lang.Boolean getStrictSandbox() {
            return this.strictSandbox;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getStrictSandbox() != null) {
                data.set("strictSandbox", om.valueToTree(this.getStrictSandbox()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecWasm"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecWasm.Jsii$Proxy that = (ConfigurationSpecWasm.Jsii$Proxy) o;

            return this.strictSandbox != null ? this.strictSandbox.equals(that.strictSandbox) : that.strictSandbox == null;
        }

        @Override
        public final int hashCode() {
            int result = this.strictSandbox != null ? this.strictSandbox.hashCode() : 0;
            return result;
        }
    }
}
