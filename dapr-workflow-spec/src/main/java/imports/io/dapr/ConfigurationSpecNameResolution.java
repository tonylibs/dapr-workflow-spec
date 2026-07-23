package imports.io.dapr;

/**
 * NameResolutionSpec is the spec for name resolution configuration.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.449Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecNameResolution")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecNameResolution.Jsii$Proxy.class)
public interface ConfigurationSpecNameResolution extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getComponent();

    /**
     * DynamicValue is a dynamic value struct for the component.metadata pair value.
     */
    @org.jetbrains.annotations.NotNull java.lang.Object getConfiguration();

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getVersion();

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecNameResolution}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecNameResolution}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecNameResolution> {
        java.lang.String component;
        java.lang.Object configuration;
        java.lang.String version;

        /**
         * Sets the value of {@link ConfigurationSpecNameResolution#getComponent}
         * @param component the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder component(java.lang.String component) {
            this.component = component;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecNameResolution#getConfiguration}
         * @param configuration DynamicValue is a dynamic value struct for the component.metadata pair value. This parameter is required.
         * @return {@code this}
         */
        public Builder configuration(java.lang.Object configuration) {
            this.configuration = configuration;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecNameResolution#getVersion}
         * @param version the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder version(java.lang.String version) {
            this.version = version;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecNameResolution}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecNameResolution build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecNameResolution}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecNameResolution {
        private final java.lang.String component;
        private final java.lang.Object configuration;
        private final java.lang.String version;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.component = software.amazon.jsii.Kernel.get(this, "component", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.configuration = software.amazon.jsii.Kernel.get(this, "configuration", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
            this.version = software.amazon.jsii.Kernel.get(this, "version", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.component = java.util.Objects.requireNonNull(builder.component, "component is required");
            this.configuration = java.util.Objects.requireNonNull(builder.configuration, "configuration is required");
            this.version = java.util.Objects.requireNonNull(builder.version, "version is required");
        }

        @Override
        public final java.lang.String getComponent() {
            return this.component;
        }

        @Override
        public final java.lang.Object getConfiguration() {
            return this.configuration;
        }

        @Override
        public final java.lang.String getVersion() {
            return this.version;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("component", om.valueToTree(this.getComponent()));
            data.set("configuration", om.valueToTree(this.getConfiguration()));
            data.set("version", om.valueToTree(this.getVersion()));

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecNameResolution"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecNameResolution.Jsii$Proxy that = (ConfigurationSpecNameResolution.Jsii$Proxy) o;

            if (!component.equals(that.component)) return false;
            if (!configuration.equals(that.configuration)) return false;
            return this.version.equals(that.version);
        }

        @Override
        public final int hashCode() {
            int result = this.component.hashCode();
            result = 31 * result + (this.configuration.hashCode());
            result = 31 * result + (this.version.hashCode());
            return result;
        }
    }
}
