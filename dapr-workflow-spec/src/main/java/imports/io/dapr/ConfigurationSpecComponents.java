package imports.io.dapr;

/**
 * ComponentsSpec describes the configuration for Dapr components.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.439Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecComponents")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecComponents.Jsii$Proxy.class)
public interface ConfigurationSpecComponents extends software.amazon.jsii.JsiiSerializable {

    /**
     * Denylist of component types that cannot be instantiated.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getDeny() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecComponents}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecComponents}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecComponents> {
        java.util.List<java.lang.String> deny;

        /**
         * Sets the value of {@link ConfigurationSpecComponents#getDeny}
         * @param deny Denylist of component types that cannot be instantiated.
         * @return {@code this}
         */
        public Builder deny(java.util.List<java.lang.String> deny) {
            this.deny = deny;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecComponents}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecComponents build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecComponents}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecComponents {
        private final java.util.List<java.lang.String> deny;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.deny = software.amazon.jsii.Kernel.get(this, "deny", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.deny = builder.deny;
        }

        @Override
        public final java.util.List<java.lang.String> getDeny() {
            return this.deny;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getDeny() != null) {
                data.set("deny", om.valueToTree(this.getDeny()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecComponents"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecComponents.Jsii$Proxy that = (ConfigurationSpecComponents.Jsii$Proxy) o;

            return this.deny != null ? this.deny.equals(that.deny) : that.deny == null;
        }

        @Override
        public final int hashCode() {
            int result = this.deny != null ? this.deny.hashCode() : 0;
            return result;
        }
    }
}
