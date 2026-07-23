package imports.dev.knative.internal.networking;

/**
 * IngressBackendSplit describes all endpoints for a given service and port.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:14.129Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.networking.$Module.class, fqn = "devknativeinternalnetworking.IngressSpecRulesHttpPathsSplits")
@software.amazon.jsii.Jsii.Proxy(IngressSpecRulesHttpPathsSplits.Jsii$Proxy.class)
public interface IngressSpecRulesHttpPathsSplits extends software.amazon.jsii.JsiiSerializable {

    /**
     * Specifies the name of the referenced service.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getServiceName();

    /**
     * Specifies the namespace of the referenced service.
     * <p>
     * NOTE: This differs from K8s Ingress to allow routing to different namespaces.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getServiceNamespace();

    /**
     * Specifies the port of the referenced service.
     */
    @org.jetbrains.annotations.NotNull imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplitsServicePort getServicePort();

    /**
     * AppendHeaders allow specifying additional HTTP headers to add before forwarding a request to the destination service.
     * <p>
     * NOTE: This differs from K8s Ingress which doesn't allow header appending.
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, java.lang.String> getAppendHeaders() {
        return null;
    }

    /**
     * Specifies the split percentage, a number between 0 and 100.
     * <p>
     * If
     * only one split is specified, we default to 100.
     * <p>
     * NOTE: This differs from K8s Ingress to allow percentage split.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getPercent() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link IngressSpecRulesHttpPathsSplits}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link IngressSpecRulesHttpPathsSplits}
     */
    public static final class Builder implements software.amazon.jsii.Builder<IngressSpecRulesHttpPathsSplits> {
        java.lang.String serviceName;
        java.lang.String serviceNamespace;
        imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplitsServicePort servicePort;
        java.util.Map<java.lang.String, java.lang.String> appendHeaders;
        java.lang.Number percent;

        /**
         * Sets the value of {@link IngressSpecRulesHttpPathsSplits#getServiceName}
         * @param serviceName Specifies the name of the referenced service. This parameter is required.
         * @return {@code this}
         */
        public Builder serviceName(java.lang.String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        /**
         * Sets the value of {@link IngressSpecRulesHttpPathsSplits#getServiceNamespace}
         * @param serviceNamespace Specifies the namespace of the referenced service. This parameter is required.
         *                         NOTE: This differs from K8s Ingress to allow routing to different namespaces.
         * @return {@code this}
         */
        public Builder serviceNamespace(java.lang.String serviceNamespace) {
            this.serviceNamespace = serviceNamespace;
            return this;
        }

        /**
         * Sets the value of {@link IngressSpecRulesHttpPathsSplits#getServicePort}
         * @param servicePort Specifies the port of the referenced service. This parameter is required.
         * @return {@code this}
         */
        public Builder servicePort(imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplitsServicePort servicePort) {
            this.servicePort = servicePort;
            return this;
        }

        /**
         * Sets the value of {@link IngressSpecRulesHttpPathsSplits#getAppendHeaders}
         * @param appendHeaders AppendHeaders allow specifying additional HTTP headers to add before forwarding a request to the destination service.
         *                      NOTE: This differs from K8s Ingress which doesn't allow header appending.
         * @return {@code this}
         */
        public Builder appendHeaders(java.util.Map<java.lang.String, java.lang.String> appendHeaders) {
            this.appendHeaders = appendHeaders;
            return this;
        }

        /**
         * Sets the value of {@link IngressSpecRulesHttpPathsSplits#getPercent}
         * @param percent Specifies the split percentage, a number between 0 and 100.
         *                If
         *                only one split is specified, we default to 100.
         *                <p>
         *                NOTE: This differs from K8s Ingress to allow percentage split.
         * @return {@code this}
         */
        public Builder percent(java.lang.Number percent) {
            this.percent = percent;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link IngressSpecRulesHttpPathsSplits}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public IngressSpecRulesHttpPathsSplits build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link IngressSpecRulesHttpPathsSplits}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements IngressSpecRulesHttpPathsSplits {
        private final java.lang.String serviceName;
        private final java.lang.String serviceNamespace;
        private final imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplitsServicePort servicePort;
        private final java.util.Map<java.lang.String, java.lang.String> appendHeaders;
        private final java.lang.Number percent;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.serviceName = software.amazon.jsii.Kernel.get(this, "serviceName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.serviceNamespace = software.amazon.jsii.Kernel.get(this, "serviceNamespace", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.servicePort = software.amazon.jsii.Kernel.get(this, "servicePort", software.amazon.jsii.NativeType.forClass(imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplitsServicePort.class));
            this.appendHeaders = software.amazon.jsii.Kernel.get(this, "appendHeaders", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.percent = software.amazon.jsii.Kernel.get(this, "percent", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.serviceName = java.util.Objects.requireNonNull(builder.serviceName, "serviceName is required");
            this.serviceNamespace = java.util.Objects.requireNonNull(builder.serviceNamespace, "serviceNamespace is required");
            this.servicePort = java.util.Objects.requireNonNull(builder.servicePort, "servicePort is required");
            this.appendHeaders = builder.appendHeaders;
            this.percent = builder.percent;
        }

        @Override
        public final java.lang.String getServiceName() {
            return this.serviceName;
        }

        @Override
        public final java.lang.String getServiceNamespace() {
            return this.serviceNamespace;
        }

        @Override
        public final imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplitsServicePort getServicePort() {
            return this.servicePort;
        }

        @Override
        public final java.util.Map<java.lang.String, java.lang.String> getAppendHeaders() {
            return this.appendHeaders;
        }

        @Override
        public final java.lang.Number getPercent() {
            return this.percent;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("serviceName", om.valueToTree(this.getServiceName()));
            data.set("serviceNamespace", om.valueToTree(this.getServiceNamespace()));
            data.set("servicePort", om.valueToTree(this.getServicePort()));
            if (this.getAppendHeaders() != null) {
                data.set("appendHeaders", om.valueToTree(this.getAppendHeaders()));
            }
            if (this.getPercent() != null) {
                data.set("percent", om.valueToTree(this.getPercent()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeinternalnetworking.IngressSpecRulesHttpPathsSplits"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            IngressSpecRulesHttpPathsSplits.Jsii$Proxy that = (IngressSpecRulesHttpPathsSplits.Jsii$Proxy) o;

            if (!serviceName.equals(that.serviceName)) return false;
            if (!serviceNamespace.equals(that.serviceNamespace)) return false;
            if (!servicePort.equals(that.servicePort)) return false;
            if (this.appendHeaders != null ? !this.appendHeaders.equals(that.appendHeaders) : that.appendHeaders != null) return false;
            return this.percent != null ? this.percent.equals(that.percent) : that.percent == null;
        }

        @Override
        public final int hashCode() {
            int result = this.serviceName.hashCode();
            result = 31 * result + (this.serviceNamespace.hashCode());
            result = 31 * result + (this.servicePort.hashCode());
            result = 31 * result + (this.appendHeaders != null ? this.appendHeaders.hashCode() : 0);
            result = 31 * result + (this.percent != null ? this.percent.hashCode() : 0);
            return result;
        }
    }
}
