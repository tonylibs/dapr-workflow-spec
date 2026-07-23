package imports.io.dapr;

/**
 * SelectorSpec selects target services to which the handler is to be applied.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.440Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecHttpPipelineHandlersSelector")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecHttpPipelineHandlersSelector.Jsii$Proxy.class)
public interface ConfigurationSpecHttpPipelineHandlersSelector extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.util.List<imports.io.dapr.ConfigurationSpecHttpPipelineHandlersSelectorFields> getFields();

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecHttpPipelineHandlersSelector}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecHttpPipelineHandlersSelector}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecHttpPipelineHandlersSelector> {
        java.util.List<imports.io.dapr.ConfigurationSpecHttpPipelineHandlersSelectorFields> fields;

        /**
         * Sets the value of {@link ConfigurationSpecHttpPipelineHandlersSelector#getFields}
         * @param fields the value to be set. This parameter is required.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder fields(java.util.List<? extends imports.io.dapr.ConfigurationSpecHttpPipelineHandlersSelectorFields> fields) {
            this.fields = (java.util.List<imports.io.dapr.ConfigurationSpecHttpPipelineHandlersSelectorFields>)fields;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecHttpPipelineHandlersSelector}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecHttpPipelineHandlersSelector build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecHttpPipelineHandlersSelector}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecHttpPipelineHandlersSelector {
        private final java.util.List<imports.io.dapr.ConfigurationSpecHttpPipelineHandlersSelectorFields> fields;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.fields = software.amazon.jsii.Kernel.get(this, "fields", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecHttpPipelineHandlersSelectorFields.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.fields = (java.util.List<imports.io.dapr.ConfigurationSpecHttpPipelineHandlersSelectorFields>)java.util.Objects.requireNonNull(builder.fields, "fields is required");
        }

        @Override
        public final java.util.List<imports.io.dapr.ConfigurationSpecHttpPipelineHandlersSelectorFields> getFields() {
            return this.fields;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("fields", om.valueToTree(this.getFields()));

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecHttpPipelineHandlersSelector"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecHttpPipelineHandlersSelector.Jsii$Proxy that = (ConfigurationSpecHttpPipelineHandlersSelector.Jsii$Proxy) o;

            return this.fields.equals(that.fields);
        }

        @Override
        public final int hashCode() {
            int result = this.fields.hashCode();
            return result;
        }
    }
}
