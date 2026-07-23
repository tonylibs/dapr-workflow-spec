package imports.dev.knative.serving;

/**
 * Compute Resources required by this container.
 * <p>
 * Cannot be updated.
 * More info: https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.189Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ServiceSpecTemplateSpecContainersResources")
@software.amazon.jsii.Jsii.Proxy(ServiceSpecTemplateSpecContainersResources.Jsii$Proxy.class)
public interface ServiceSpecTemplateSpecContainersResources extends software.amazon.jsii.JsiiSerializable {

    /**
     * Limits describes the maximum amount of compute resources allowed.
     * <p>
     * More info: https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesLimits> getLimits() {
        return null;
    }

    /**
     * Requests describes the minimum amount of compute resources required.
     * <p>
     * If Requests is omitted for a container, it defaults to Limits if that is explicitly specified,
     * otherwise to an implementation-defined value. Requests cannot exceed Limits.
     * More info: https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesRequests> getRequests() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ServiceSpecTemplateSpecContainersResources}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ServiceSpecTemplateSpecContainersResources}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ServiceSpecTemplateSpecContainersResources> {
        java.util.Map<java.lang.String, imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesLimits> limits;
        java.util.Map<java.lang.String, imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesRequests> requests;

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecContainersResources#getLimits}
         * @param limits Limits describes the maximum amount of compute resources allowed.
         *               More info: https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder limits(java.util.Map<java.lang.String, ? extends imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesLimits> limits) {
            this.limits = (java.util.Map<java.lang.String, imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesLimits>)limits;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecContainersResources#getRequests}
         * @param requests Requests describes the minimum amount of compute resources required.
         *                 If Requests is omitted for a container, it defaults to Limits if that is explicitly specified,
         *                 otherwise to an implementation-defined value. Requests cannot exceed Limits.
         *                 More info: https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder requests(java.util.Map<java.lang.String, ? extends imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesRequests> requests) {
            this.requests = (java.util.Map<java.lang.String, imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesRequests>)requests;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ServiceSpecTemplateSpecContainersResources}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ServiceSpecTemplateSpecContainersResources build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ServiceSpecTemplateSpecContainersResources}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ServiceSpecTemplateSpecContainersResources {
        private final java.util.Map<java.lang.String, imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesLimits> limits;
        private final java.util.Map<java.lang.String, imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesRequests> requests;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.limits = software.amazon.jsii.Kernel.get(this, "limits", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesLimits.class)));
            this.requests = software.amazon.jsii.Kernel.get(this, "requests", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesRequests.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.limits = (java.util.Map<java.lang.String, imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesLimits>)builder.limits;
            this.requests = (java.util.Map<java.lang.String, imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesRequests>)builder.requests;
        }

        @Override
        public final java.util.Map<java.lang.String, imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesLimits> getLimits() {
            return this.limits;
        }

        @Override
        public final java.util.Map<java.lang.String, imports.dev.knative.serving.ServiceSpecTemplateSpecContainersResourcesRequests> getRequests() {
            return this.requests;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getLimits() != null) {
                data.set("limits", om.valueToTree(this.getLimits()));
            }
            if (this.getRequests() != null) {
                data.set("requests", om.valueToTree(this.getRequests()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ServiceSpecTemplateSpecContainersResources"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ServiceSpecTemplateSpecContainersResources.Jsii$Proxy that = (ServiceSpecTemplateSpecContainersResources.Jsii$Proxy) o;

            if (this.limits != null ? !this.limits.equals(that.limits) : that.limits != null) return false;
            return this.requests != null ? this.requests.equals(that.requests) : that.requests == null;
        }

        @Override
        public final int hashCode() {
            int result = this.limits != null ? this.limits.hashCode() : 0;
            result = 31 * result + (this.requests != null ? this.requests.hashCode() : 0);
            return result;
        }
    }
}
