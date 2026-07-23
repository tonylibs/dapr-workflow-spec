package imports.io.dapr;

/**
 * MetricsLabel defines an object that allows to set regex expressions for a label.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.442Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecMetricRulesLabels")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecMetricRulesLabels.Jsii$Proxy.class)
public interface ConfigurationSpecMetricRulesLabels extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getName();

    /**
     */
    @org.jetbrains.annotations.NotNull java.util.Map<java.lang.String, java.lang.String> getRegex();

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecMetricRulesLabels}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecMetricRulesLabels}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecMetricRulesLabels> {
        java.lang.String name;
        java.util.Map<java.lang.String, java.lang.String> regex;

        /**
         * Sets the value of {@link ConfigurationSpecMetricRulesLabels#getName}
         * @param name the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder name(java.lang.String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecMetricRulesLabels#getRegex}
         * @param regex the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder regex(java.util.Map<java.lang.String, java.lang.String> regex) {
            this.regex = regex;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecMetricRulesLabels}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecMetricRulesLabels build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecMetricRulesLabels}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecMetricRulesLabels {
        private final java.lang.String name;
        private final java.util.Map<java.lang.String, java.lang.String> regex;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.regex = software.amazon.jsii.Kernel.get(this, "regex", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.name = java.util.Objects.requireNonNull(builder.name, "name is required");
            this.regex = java.util.Objects.requireNonNull(builder.regex, "regex is required");
        }

        @Override
        public final java.lang.String getName() {
            return this.name;
        }

        @Override
        public final java.util.Map<java.lang.String, java.lang.String> getRegex() {
            return this.regex;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("name", om.valueToTree(this.getName()));
            data.set("regex", om.valueToTree(this.getRegex()));

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecMetricRulesLabels"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecMetricRulesLabels.Jsii$Proxy that = (ConfigurationSpecMetricRulesLabels.Jsii$Proxy) o;

            if (!name.equals(that.name)) return false;
            return this.regex.equals(that.regex);
        }

        @Override
        public final int hashCode() {
            int result = this.name.hashCode();
            result = 31 * result + (this.regex.hashCode());
            return result;
        }
    }
}
