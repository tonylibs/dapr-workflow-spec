package imports.dev.knative.serving;

/**
 * Selects a resource of the container: only resources limits and requests (limits.cpu, limits.memory, requests.cpu and requests.memory) are currently supported.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.168Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef")
@software.amazon.jsii.Jsii.Proxy(RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef.Jsii$Proxy.class)
public interface RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef extends software.amazon.jsii.JsiiSerializable {

    /**
     * Required: resource to select.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getResource();

    /**
     * Container name: required for volumes, optional for env vars.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getContainerName() {
        return null;
    }

    /**
     * Specifies the output format of the exposed resources, defaults to "1".
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor getDivisor() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef}
     */
    public static final class Builder implements software.amazon.jsii.Builder<RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef> {
        java.lang.String resource;
        java.lang.String containerName;
        imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor divisor;

        /**
         * Sets the value of {@link RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef#getResource}
         * @param resource Required: resource to select. This parameter is required.
         * @return {@code this}
         */
        public Builder resource(java.lang.String resource) {
            this.resource = resource;
            return this;
        }

        /**
         * Sets the value of {@link RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef#getContainerName}
         * @param containerName Container name: required for volumes, optional for env vars.
         * @return {@code this}
         */
        public Builder containerName(java.lang.String containerName) {
            this.containerName = containerName;
            return this;
        }

        /**
         * Sets the value of {@link RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef#getDivisor}
         * @param divisor Specifies the output format of the exposed resources, defaults to "1".
         * @return {@code this}
         */
        public Builder divisor(imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor divisor) {
            this.divisor = divisor;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef {
        private final java.lang.String resource;
        private final java.lang.String containerName;
        private final imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor divisor;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.resource = software.amazon.jsii.Kernel.get(this, "resource", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.containerName = software.amazon.jsii.Kernel.get(this, "containerName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.divisor = software.amazon.jsii.Kernel.get(this, "divisor", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.resource = java.util.Objects.requireNonNull(builder.resource, "resource is required");
            this.containerName = builder.containerName;
            this.divisor = builder.divisor;
        }

        @Override
        public final java.lang.String getResource() {
            return this.resource;
        }

        @Override
        public final java.lang.String getContainerName() {
            return this.containerName;
        }

        @Override
        public final imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor getDivisor() {
            return this.divisor;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("resource", om.valueToTree(this.getResource()));
            if (this.getContainerName() != null) {
                data.set("containerName", om.valueToTree(this.getContainerName()));
            }
            if (this.getDivisor() != null) {
                data.set("divisor", om.valueToTree(this.getDivisor()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef.Jsii$Proxy that = (RevisionSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef.Jsii$Proxy) o;

            if (!resource.equals(that.resource)) return false;
            if (this.containerName != null ? !this.containerName.equals(that.containerName) : that.containerName != null) return false;
            return this.divisor != null ? this.divisor.equals(that.divisor) : that.divisor == null;
        }

        @Override
        public final int hashCode() {
            int result = this.resource.hashCode();
            result = 31 * result + (this.containerName != null ? this.containerName.hashCode() : 0);
            result = 31 * result + (this.divisor != null ? this.divisor.hashCode() : 0);
            return result;
        }
    }
}
