package imports.io.dapr;

/**
 * PipelineSpec defines the middleware pipeline.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.440Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecHttpPipeline")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecHttpPipeline.Jsii$Proxy.class)
public interface ConfigurationSpecHttpPipeline extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.util.List<imports.io.dapr.ConfigurationSpecHttpPipelineHandlers> getHandlers();

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecHttpPipeline}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecHttpPipeline}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecHttpPipeline> {
        java.util.List<imports.io.dapr.ConfigurationSpecHttpPipelineHandlers> handlers;

        /**
         * Sets the value of {@link ConfigurationSpecHttpPipeline#getHandlers}
         * @param handlers the value to be set. This parameter is required.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder handlers(java.util.List<? extends imports.io.dapr.ConfigurationSpecHttpPipelineHandlers> handlers) {
            this.handlers = (java.util.List<imports.io.dapr.ConfigurationSpecHttpPipelineHandlers>)handlers;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecHttpPipeline}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecHttpPipeline build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecHttpPipeline}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecHttpPipeline {
        private final java.util.List<imports.io.dapr.ConfigurationSpecHttpPipelineHandlers> handlers;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.handlers = software.amazon.jsii.Kernel.get(this, "handlers", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecHttpPipelineHandlers.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.handlers = (java.util.List<imports.io.dapr.ConfigurationSpecHttpPipelineHandlers>)java.util.Objects.requireNonNull(builder.handlers, "handlers is required");
        }

        @Override
        public final java.util.List<imports.io.dapr.ConfigurationSpecHttpPipelineHandlers> getHandlers() {
            return this.handlers;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("handlers", om.valueToTree(this.getHandlers()));

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecHttpPipeline"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecHttpPipeline.Jsii$Proxy that = (ConfigurationSpecHttpPipeline.Jsii$Proxy) o;

            return this.handlers.equals(that.handlers);
        }

        @Override
        public final int hashCode() {
            int result = this.handlers.hashCode();
            return result;
        }
    }
}
