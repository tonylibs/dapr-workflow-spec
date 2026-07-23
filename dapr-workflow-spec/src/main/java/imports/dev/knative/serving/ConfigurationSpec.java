package imports.dev.knative.serving;

/**
 * ConfigurationSpec holds the desired state of the Configuration (from the client).
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.097Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ConfigurationSpec")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpec.Jsii$Proxy.class)
public interface ConfigurationSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     * Template holds the latest specification for the Revision to be stamped out.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ConfigurationSpecTemplate getTemplate() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpec> {
        imports.dev.knative.serving.ConfigurationSpecTemplate template;

        /**
         * Sets the value of {@link ConfigurationSpec#getTemplate}
         * @param template Template holds the latest specification for the Revision to be stamped out.
         * @return {@code this}
         */
        public Builder template(imports.dev.knative.serving.ConfigurationSpecTemplate template) {
            this.template = template;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpec {
        private final imports.dev.knative.serving.ConfigurationSpecTemplate template;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.template = software.amazon.jsii.Kernel.get(this, "template", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplate.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.template = builder.template;
        }

        @Override
        public final imports.dev.knative.serving.ConfigurationSpecTemplate getTemplate() {
            return this.template;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getTemplate() != null) {
                data.set("template", om.valueToTree(this.getTemplate()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ConfigurationSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpec.Jsii$Proxy that = (ConfigurationSpec.Jsii$Proxy) o;

            return this.template != null ? this.template.equals(that.template) : that.template == null;
        }

        @Override
        public final int hashCode() {
            int result = this.template != null ? this.template.hashCode() : 0;
            return result;
        }
    }
}
