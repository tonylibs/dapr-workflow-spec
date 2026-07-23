package imports.dev.knative.serving;

/**
 * DownwardAPIVolumeFile represents information to create the file containing the pod field.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.193Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems")
@software.amazon.jsii.Jsii.Proxy(ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems.Jsii$Proxy.class)
public interface ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems extends software.amazon.jsii.JsiiSerializable {

    /**
     * Required: Path is  the relative path name of the file to be created.
     * <p>
     * Must not be absolute or contain the '..' path. Must be utf-8 encoded. The first item of the relative path must not start with '..'
     */
    @org.jetbrains.annotations.NotNull java.lang.String getPath();

    /**
     * Required: Selects a field of the pod: only annotations, labels, name, namespace and uid are supported.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef getFieldRef() {
        return null;
    }

    /**
     * Optional: mode bits used to set permissions on this file, must be an octal value between 0000 and 0777 or a decimal value between 0 and 511.
     * <p>
     * YAML accepts both octal and decimal values, JSON requires decimal values for mode bits.
     * If not specified, the volume defaultMode will be used.
     * This might be in conflict with other options that affect the file
     * mode, like fsGroup, and the result can be other mode bits set.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getMode() {
        return null;
    }

    /**
     * Selects a resource of the container: only resources limits and requests (limits.cpu, limits.memory, requests.cpu and requests.memory) are currently supported.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef getResourceFieldRef() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems> {
        java.lang.String path;
        imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef fieldRef;
        java.lang.Number mode;
        imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef resourceFieldRef;

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems#getPath}
         * @param path Required: Path is  the relative path name of the file to be created. This parameter is required.
         *             Must not be absolute or contain the '..' path. Must be utf-8 encoded. The first item of the relative path must not start with '..'
         * @return {@code this}
         */
        public Builder path(java.lang.String path) {
            this.path = path;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems#getFieldRef}
         * @param fieldRef Required: Selects a field of the pod: only annotations, labels, name, namespace and uid are supported.
         * @return {@code this}
         */
        public Builder fieldRef(imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef fieldRef) {
            this.fieldRef = fieldRef;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems#getMode}
         * @param mode Optional: mode bits used to set permissions on this file, must be an octal value between 0000 and 0777 or a decimal value between 0 and 511.
         *             YAML accepts both octal and decimal values, JSON requires decimal values for mode bits.
         *             If not specified, the volume defaultMode will be used.
         *             This might be in conflict with other options that affect the file
         *             mode, like fsGroup, and the result can be other mode bits set.
         * @return {@code this}
         */
        public Builder mode(java.lang.Number mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems#getResourceFieldRef}
         * @param resourceFieldRef Selects a resource of the container: only resources limits and requests (limits.cpu, limits.memory, requests.cpu and requests.memory) are currently supported.
         * @return {@code this}
         */
        public Builder resourceFieldRef(imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef resourceFieldRef) {
            this.resourceFieldRef = resourceFieldRef;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems {
        private final java.lang.String path;
        private final imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef fieldRef;
        private final java.lang.Number mode;
        private final imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef resourceFieldRef;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.path = software.amazon.jsii.Kernel.get(this, "path", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.fieldRef = software.amazon.jsii.Kernel.get(this, "fieldRef", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef.class));
            this.mode = software.amazon.jsii.Kernel.get(this, "mode", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.resourceFieldRef = software.amazon.jsii.Kernel.get(this, "resourceFieldRef", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.path = java.util.Objects.requireNonNull(builder.path, "path is required");
            this.fieldRef = builder.fieldRef;
            this.mode = builder.mode;
            this.resourceFieldRef = builder.resourceFieldRef;
        }

        @Override
        public final java.lang.String getPath() {
            return this.path;
        }

        @Override
        public final imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef getFieldRef() {
            return this.fieldRef;
        }

        @Override
        public final java.lang.Number getMode() {
            return this.mode;
        }

        @Override
        public final imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRef getResourceFieldRef() {
            return this.resourceFieldRef;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("path", om.valueToTree(this.getPath()));
            if (this.getFieldRef() != null) {
                data.set("fieldRef", om.valueToTree(this.getFieldRef()));
            }
            if (this.getMode() != null) {
                data.set("mode", om.valueToTree(this.getMode()));
            }
            if (this.getResourceFieldRef() != null) {
                data.set("resourceFieldRef", om.valueToTree(this.getResourceFieldRef()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems.Jsii$Proxy that = (ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItems.Jsii$Proxy) o;

            if (!path.equals(that.path)) return false;
            if (this.fieldRef != null ? !this.fieldRef.equals(that.fieldRef) : that.fieldRef != null) return false;
            if (this.mode != null ? !this.mode.equals(that.mode) : that.mode != null) return false;
            return this.resourceFieldRef != null ? this.resourceFieldRef.equals(that.resourceFieldRef) : that.resourceFieldRef == null;
        }

        @Override
        public final int hashCode() {
            int result = this.path.hashCode();
            result = 31 * result + (this.fieldRef != null ? this.fieldRef.hashCode() : 0);
            result = 31 * result + (this.mode != null ? this.mode.hashCode() : 0);
            result = 31 * result + (this.resourceFieldRef != null ? this.resourceFieldRef.hashCode() : 0);
            return result;
        }
    }
}
