package imports.io.dapr;

/**
 * ValidatorSpec contains additional token validators to use.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.448Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecMtlsTokenValidators")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecMtlsTokenValidators.Jsii$Proxy.class)
public interface ConfigurationSpecMtlsTokenValidators extends software.amazon.jsii.JsiiSerializable {

    /**
     * Name of the validator.
     */
    @org.jetbrains.annotations.NotNull imports.io.dapr.ConfigurationSpecMtlsTokenValidatorsName getName();

    /**
     * Options for the validator, if any.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Object getOptions() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecMtlsTokenValidators}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecMtlsTokenValidators}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecMtlsTokenValidators> {
        imports.io.dapr.ConfigurationSpecMtlsTokenValidatorsName name;
        java.lang.Object options;

        /**
         * Sets the value of {@link ConfigurationSpecMtlsTokenValidators#getName}
         * @param name Name of the validator. This parameter is required.
         * @return {@code this}
         */
        public Builder name(imports.io.dapr.ConfigurationSpecMtlsTokenValidatorsName name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecMtlsTokenValidators#getOptions}
         * @param options Options for the validator, if any.
         * @return {@code this}
         */
        public Builder options(java.lang.Object options) {
            this.options = options;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecMtlsTokenValidators}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecMtlsTokenValidators build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecMtlsTokenValidators}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecMtlsTokenValidators {
        private final imports.io.dapr.ConfigurationSpecMtlsTokenValidatorsName name;
        private final java.lang.Object options;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecMtlsTokenValidatorsName.class));
            this.options = software.amazon.jsii.Kernel.get(this, "options", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.name = java.util.Objects.requireNonNull(builder.name, "name is required");
            this.options = builder.options;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecMtlsTokenValidatorsName getName() {
            return this.name;
        }

        @Override
        public final java.lang.Object getOptions() {
            return this.options;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("name", om.valueToTree(this.getName()));
            if (this.getOptions() != null) {
                data.set("options", om.valueToTree(this.getOptions()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecMtlsTokenValidators"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecMtlsTokenValidators.Jsii$Proxy that = (ConfigurationSpecMtlsTokenValidators.Jsii$Proxy) o;

            if (!name.equals(that.name)) return false;
            return this.options != null ? this.options.equals(that.options) : that.options == null;
        }

        @Override
        public final int hashCode() {
            int result = this.name.hashCode();
            result = 31 * result + (this.options != null ? this.options.hashCode() : 0);
            return result;
        }
    }
}
