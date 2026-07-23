package imports.io.dapr;

/**
 * MetricsRule defines configuration options for a metric.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.442Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecMetricRules")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecMetricRules.Jsii$Proxy.class)
public interface ConfigurationSpecMetricRules extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.util.List<imports.io.dapr.ConfigurationSpecMetricRulesLabels> getLabels();

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getName();

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecMetricRules}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecMetricRules}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecMetricRules> {
        java.util.List<imports.io.dapr.ConfigurationSpecMetricRulesLabels> labels;
        java.lang.String name;

        /**
         * Sets the value of {@link ConfigurationSpecMetricRules#getLabels}
         * @param labels the value to be set. This parameter is required.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder labels(java.util.List<? extends imports.io.dapr.ConfigurationSpecMetricRulesLabels> labels) {
            this.labels = (java.util.List<imports.io.dapr.ConfigurationSpecMetricRulesLabels>)labels;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecMetricRules#getName}
         * @param name the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder name(java.lang.String name) {
            this.name = name;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecMetricRules}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecMetricRules build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecMetricRules}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecMetricRules {
        private final java.util.List<imports.io.dapr.ConfigurationSpecMetricRulesLabels> labels;
        private final java.lang.String name;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.labels = software.amazon.jsii.Kernel.get(this, "labels", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecMetricRulesLabels.class)));
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.labels = (java.util.List<imports.io.dapr.ConfigurationSpecMetricRulesLabels>)java.util.Objects.requireNonNull(builder.labels, "labels is required");
            this.name = java.util.Objects.requireNonNull(builder.name, "name is required");
        }

        @Override
        public final java.util.List<imports.io.dapr.ConfigurationSpecMetricRulesLabels> getLabels() {
            return this.labels;
        }

        @Override
        public final java.lang.String getName() {
            return this.name;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("labels", om.valueToTree(this.getLabels()));
            data.set("name", om.valueToTree(this.getName()));

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecMetricRules"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecMetricRules.Jsii$Proxy that = (ConfigurationSpecMetricRules.Jsii$Proxy) o;

            if (!labels.equals(that.labels)) return false;
            return this.name.equals(that.name);
        }

        @Override
        public final int hashCode() {
            int result = this.labels.hashCode();
            result = 31 * result + (this.name.hashCode());
            return result;
        }
    }
}
