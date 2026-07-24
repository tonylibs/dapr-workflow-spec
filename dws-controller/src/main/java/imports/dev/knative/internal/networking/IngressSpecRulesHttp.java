package imports.dev.knative.internal.networking;

/**
 * HTTP represents a rule to apply against incoming requests.
 * <p>
 * If the
 * rule is satisfied, the request is routed to the specified backend.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:14.127Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.networking.$Module.class, fqn = "devknativeinternalnetworking.IngressSpecRulesHttp")
@software.amazon.jsii.Jsii.Proxy(IngressSpecRulesHttp.Jsii$Proxy.class)
public interface IngressSpecRulesHttp extends software.amazon.jsii.JsiiSerializable {

    /**
     * A collection of paths that map requests to backends.
     * <p>
     * If they are multiple matching paths, the first match takes precedence.
     */
    @org.jetbrains.annotations.NotNull java.util.List<imports.dev.knative.internal.networking.IngressSpecRulesHttpPaths> getPaths();

    /**
     * @return a {@link Builder} of {@link IngressSpecRulesHttp}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link IngressSpecRulesHttp}
     */
    public static final class Builder implements software.amazon.jsii.Builder<IngressSpecRulesHttp> {
        java.util.List<imports.dev.knative.internal.networking.IngressSpecRulesHttpPaths> paths;

        /**
         * Sets the value of {@link IngressSpecRulesHttp#getPaths}
         * @param paths A collection of paths that map requests to backends. This parameter is required.
         *              If they are multiple matching paths, the first match takes precedence.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder paths(java.util.List<? extends imports.dev.knative.internal.networking.IngressSpecRulesHttpPaths> paths) {
            this.paths = (java.util.List<imports.dev.knative.internal.networking.IngressSpecRulesHttpPaths>)paths;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link IngressSpecRulesHttp}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public IngressSpecRulesHttp build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link IngressSpecRulesHttp}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements IngressSpecRulesHttp {
        private final java.util.List<imports.dev.knative.internal.networking.IngressSpecRulesHttpPaths> paths;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.paths = software.amazon.jsii.Kernel.get(this, "paths", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.internal.networking.IngressSpecRulesHttpPaths.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.paths = (java.util.List<imports.dev.knative.internal.networking.IngressSpecRulesHttpPaths>)java.util.Objects.requireNonNull(builder.paths, "paths is required");
        }

        @Override
        public final java.util.List<imports.dev.knative.internal.networking.IngressSpecRulesHttpPaths> getPaths() {
            return this.paths;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("paths", om.valueToTree(this.getPaths()));

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeinternalnetworking.IngressSpecRulesHttp"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            IngressSpecRulesHttp.Jsii$Proxy that = (IngressSpecRulesHttp.Jsii$Proxy) o;

            return this.paths.equals(that.paths);
        }

        @Override
        public final int hashCode() {
            int result = this.paths.hashCode();
            return result;
        }
    }
}
