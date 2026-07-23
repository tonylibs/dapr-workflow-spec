package imports.dev.knative.serving;

/**
 * Spec holds the desired state of the Route (from the client).
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.172Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.RouteSpec")
@software.amazon.jsii.Jsii.Proxy(RouteSpec.Jsii$Proxy.class)
public interface RouteSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     * Traffic specifies how to distribute traffic over a collection of revisions and configurations.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.serving.RouteSpecTraffic> getTraffic() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link RouteSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link RouteSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<RouteSpec> {
        java.util.List<imports.dev.knative.serving.RouteSpecTraffic> traffic;

        /**
         * Sets the value of {@link RouteSpec#getTraffic}
         * @param traffic Traffic specifies how to distribute traffic over a collection of revisions and configurations.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder traffic(java.util.List<? extends imports.dev.knative.serving.RouteSpecTraffic> traffic) {
            this.traffic = (java.util.List<imports.dev.knative.serving.RouteSpecTraffic>)traffic;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link RouteSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public RouteSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link RouteSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements RouteSpec {
        private final java.util.List<imports.dev.knative.serving.RouteSpecTraffic> traffic;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.traffic = software.amazon.jsii.Kernel.get(this, "traffic", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.RouteSpecTraffic.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.traffic = (java.util.List<imports.dev.knative.serving.RouteSpecTraffic>)builder.traffic;
        }

        @Override
        public final java.util.List<imports.dev.knative.serving.RouteSpecTraffic> getTraffic() {
            return this.traffic;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getTraffic() != null) {
                data.set("traffic", om.valueToTree(this.getTraffic()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.RouteSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RouteSpec.Jsii$Proxy that = (RouteSpec.Jsii$Proxy) o;

            return this.traffic != null ? this.traffic.equals(that.traffic) : that.traffic == null;
        }

        @Override
        public final int hashCode() {
            int result = this.traffic != null ? this.traffic.hashCode() : 0;
            return result;
        }
    }
}
