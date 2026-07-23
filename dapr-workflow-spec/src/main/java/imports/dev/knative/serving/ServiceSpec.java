package imports.dev.knative.serving;

/**
 * ServiceSpec represents the configuration for the Service object.
 * <p>
 * A Service's specification is the union of the specifications for a Route
 * and Configuration.  The Service restricts what can be expressed in these
 * fields, e.g. the Route must reference the provided Configuration;
 * however, these limitations also enable friendlier defaulting,
 * e.g. Route never needs a Configuration name, and may be defaulted to
 * the appropriate "run latest" spec.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.181Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ServiceSpec")
@software.amazon.jsii.Jsii.Proxy(ServiceSpec.Jsii$Proxy.class)
public interface ServiceSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     * Template holds the latest specification for the Revision to be stamped out.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ServiceSpecTemplate getTemplate() {
        return null;
    }

    /**
     * Traffic specifies how to distribute traffic over a collection of revisions and configurations.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.serving.ServiceSpecTraffic> getTraffic() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ServiceSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ServiceSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ServiceSpec> {
        imports.dev.knative.serving.ServiceSpecTemplate template;
        java.util.List<imports.dev.knative.serving.ServiceSpecTraffic> traffic;

        /**
         * Sets the value of {@link ServiceSpec#getTemplate}
         * @param template Template holds the latest specification for the Revision to be stamped out.
         * @return {@code this}
         */
        public Builder template(imports.dev.knative.serving.ServiceSpecTemplate template) {
            this.template = template;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpec#getTraffic}
         * @param traffic Traffic specifies how to distribute traffic over a collection of revisions and configurations.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder traffic(java.util.List<? extends imports.dev.knative.serving.ServiceSpecTraffic> traffic) {
            this.traffic = (java.util.List<imports.dev.knative.serving.ServiceSpecTraffic>)traffic;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ServiceSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ServiceSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ServiceSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ServiceSpec {
        private final imports.dev.knative.serving.ServiceSpecTemplate template;
        private final java.util.List<imports.dev.knative.serving.ServiceSpecTraffic> traffic;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.template = software.amazon.jsii.Kernel.get(this, "template", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTemplate.class));
            this.traffic = software.amazon.jsii.Kernel.get(this, "traffic", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTraffic.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.template = builder.template;
            this.traffic = (java.util.List<imports.dev.knative.serving.ServiceSpecTraffic>)builder.traffic;
        }

        @Override
        public final imports.dev.knative.serving.ServiceSpecTemplate getTemplate() {
            return this.template;
        }

        @Override
        public final java.util.List<imports.dev.knative.serving.ServiceSpecTraffic> getTraffic() {
            return this.traffic;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getTemplate() != null) {
                data.set("template", om.valueToTree(this.getTemplate()));
            }
            if (this.getTraffic() != null) {
                data.set("traffic", om.valueToTree(this.getTraffic()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ServiceSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ServiceSpec.Jsii$Proxy that = (ServiceSpec.Jsii$Proxy) o;

            if (this.template != null ? !this.template.equals(that.template) : that.template != null) return false;
            return this.traffic != null ? this.traffic.equals(that.traffic) : that.traffic == null;
        }

        @Override
        public final int hashCode() {
            int result = this.template != null ? this.template.hashCode() : 0;
            result = 31 * result + (this.traffic != null ? this.traffic.hashCode() : 0);
            return result;
        }
    }
}
