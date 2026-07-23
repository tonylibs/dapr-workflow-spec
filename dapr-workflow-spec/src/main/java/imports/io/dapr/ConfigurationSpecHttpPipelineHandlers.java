package imports.io.dapr;

/**
 * HandlerSpec defines a request handlers.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.440Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecHttpPipelineHandlers")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecHttpPipelineHandlers.Jsii$Proxy.class)
public interface ConfigurationSpecHttpPipelineHandlers extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getName();

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getType();

    /**
     * SelectorSpec selects target services to which the handler is to be applied.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecHttpPipelineHandlersSelector getSelector() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecHttpPipelineHandlers}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecHttpPipelineHandlers}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecHttpPipelineHandlers> {
        java.lang.String name;
        java.lang.String type;
        imports.io.dapr.ConfigurationSpecHttpPipelineHandlersSelector selector;

        /**
         * Sets the value of {@link ConfigurationSpecHttpPipelineHandlers#getName}
         * @param name the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder name(java.lang.String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecHttpPipelineHandlers#getType}
         * @param type the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder type(java.lang.String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecHttpPipelineHandlers#getSelector}
         * @param selector SelectorSpec selects target services to which the handler is to be applied.
         * @return {@code this}
         */
        public Builder selector(imports.io.dapr.ConfigurationSpecHttpPipelineHandlersSelector selector) {
            this.selector = selector;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecHttpPipelineHandlers}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecHttpPipelineHandlers build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecHttpPipelineHandlers}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecHttpPipelineHandlers {
        private final java.lang.String name;
        private final java.lang.String type;
        private final imports.io.dapr.ConfigurationSpecHttpPipelineHandlersSelector selector;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.type = software.amazon.jsii.Kernel.get(this, "type", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.selector = software.amazon.jsii.Kernel.get(this, "selector", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecHttpPipelineHandlersSelector.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.name = java.util.Objects.requireNonNull(builder.name, "name is required");
            this.type = java.util.Objects.requireNonNull(builder.type, "type is required");
            this.selector = builder.selector;
        }

        @Override
        public final java.lang.String getName() {
            return this.name;
        }

        @Override
        public final java.lang.String getType() {
            return this.type;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecHttpPipelineHandlersSelector getSelector() {
            return this.selector;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("name", om.valueToTree(this.getName()));
            data.set("type", om.valueToTree(this.getType()));
            if (this.getSelector() != null) {
                data.set("selector", om.valueToTree(this.getSelector()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecHttpPipelineHandlers"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecHttpPipelineHandlers.Jsii$Proxy that = (ConfigurationSpecHttpPipelineHandlers.Jsii$Proxy) o;

            if (!name.equals(that.name)) return false;
            if (!type.equals(that.type)) return false;
            return this.selector != null ? this.selector.equals(that.selector) : that.selector == null;
        }

        @Override
        public final int hashCode() {
            int result = this.name.hashCode();
            result = 31 * result + (this.type.hashCode());
            result = 31 * result + (this.selector != null ? this.selector.hashCode() : 0);
            return result;
        }
    }
}
