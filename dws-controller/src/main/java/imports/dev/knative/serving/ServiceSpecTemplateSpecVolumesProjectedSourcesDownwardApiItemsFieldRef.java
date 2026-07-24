package imports.dev.knative.serving;

/**
 * Required: Selects a field of the pod: only annotations, labels, name, namespace and uid are supported.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.194Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef")
@software.amazon.jsii.Jsii.Proxy(ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef.Jsii$Proxy.class)
public interface ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef extends software.amazon.jsii.JsiiSerializable {

    /**
     * Path of the field to select in the specified API version.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getFieldPath();

    /**
     * Version of the schema the FieldPath is written in terms of, defaults to "v1".
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getApiVersion() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef> {
        java.lang.String fieldPath;
        java.lang.String apiVersion;

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef#getFieldPath}
         * @param fieldPath Path of the field to select in the specified API version. This parameter is required.
         * @return {@code this}
         */
        public Builder fieldPath(java.lang.String fieldPath) {
            this.fieldPath = fieldPath;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef#getApiVersion}
         * @param apiVersion Version of the schema the FieldPath is written in terms of, defaults to "v1".
         * @return {@code this}
         */
        public Builder apiVersion(java.lang.String apiVersion) {
            this.apiVersion = apiVersion;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef {
        private final java.lang.String fieldPath;
        private final java.lang.String apiVersion;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.fieldPath = software.amazon.jsii.Kernel.get(this, "fieldPath", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.apiVersion = software.amazon.jsii.Kernel.get(this, "apiVersion", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.fieldPath = java.util.Objects.requireNonNull(builder.fieldPath, "fieldPath is required");
            this.apiVersion = builder.apiVersion;
        }

        @Override
        public final java.lang.String getFieldPath() {
            return this.fieldPath;
        }

        @Override
        public final java.lang.String getApiVersion() {
            return this.apiVersion;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("fieldPath", om.valueToTree(this.getFieldPath()));
            if (this.getApiVersion() != null) {
                data.set("apiVersion", om.valueToTree(this.getApiVersion()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef.Jsii$Proxy that = (ServiceSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsFieldRef.Jsii$Proxy) o;

            if (!fieldPath.equals(that.fieldPath)) return false;
            return this.apiVersion != null ? this.apiVersion.equals(that.apiVersion) : that.apiVersion == null;
        }

        @Override
        public final int hashCode() {
            int result = this.fieldPath.hashCode();
            result = 31 * result + (this.apiVersion != null ? this.apiVersion.hashCode() : 0);
            return result;
        }
    }
}
