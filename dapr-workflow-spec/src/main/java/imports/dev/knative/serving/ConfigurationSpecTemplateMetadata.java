package imports.dev.knative.serving;

/**
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.098Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ConfigurationSpecTemplateMetadata")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecTemplateMetadata.Jsii$Proxy.class)
public interface ConfigurationSpecTemplateMetadata extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, java.lang.String> getAnnotations() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getFinalizers() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, java.lang.String> getLabels() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getName() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getNamespace() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecTemplateMetadata}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecTemplateMetadata}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecTemplateMetadata> {
        java.util.Map<java.lang.String, java.lang.String> annotations;
        java.util.List<java.lang.String> finalizers;
        java.util.Map<java.lang.String, java.lang.String> labels;
        java.lang.String name;
        java.lang.String namespace;

        /**
         * Sets the value of {@link ConfigurationSpecTemplateMetadata#getAnnotations}
         * @param annotations the value to be set.
         * @return {@code this}
         */
        public Builder annotations(java.util.Map<java.lang.String, java.lang.String> annotations) {
            this.annotations = annotations;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateMetadata#getFinalizers}
         * @param finalizers the value to be set.
         * @return {@code this}
         */
        public Builder finalizers(java.util.List<java.lang.String> finalizers) {
            this.finalizers = finalizers;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateMetadata#getLabels}
         * @param labels the value to be set.
         * @return {@code this}
         */
        public Builder labels(java.util.Map<java.lang.String, java.lang.String> labels) {
            this.labels = labels;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateMetadata#getName}
         * @param name the value to be set.
         * @return {@code this}
         */
        public Builder name(java.lang.String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateMetadata#getNamespace}
         * @param namespace the value to be set.
         * @return {@code this}
         */
        public Builder namespace(java.lang.String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecTemplateMetadata}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecTemplateMetadata build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecTemplateMetadata}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecTemplateMetadata {
        private final java.util.Map<java.lang.String, java.lang.String> annotations;
        private final java.util.List<java.lang.String> finalizers;
        private final java.util.Map<java.lang.String, java.lang.String> labels;
        private final java.lang.String name;
        private final java.lang.String namespace;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.annotations = software.amazon.jsii.Kernel.get(this, "annotations", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.finalizers = software.amazon.jsii.Kernel.get(this, "finalizers", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.labels = software.amazon.jsii.Kernel.get(this, "labels", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.namespace = software.amazon.jsii.Kernel.get(this, "namespace", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.annotations = builder.annotations;
            this.finalizers = builder.finalizers;
            this.labels = builder.labels;
            this.name = builder.name;
            this.namespace = builder.namespace;
        }

        @Override
        public final java.util.Map<java.lang.String, java.lang.String> getAnnotations() {
            return this.annotations;
        }

        @Override
        public final java.util.List<java.lang.String> getFinalizers() {
            return this.finalizers;
        }

        @Override
        public final java.util.Map<java.lang.String, java.lang.String> getLabels() {
            return this.labels;
        }

        @Override
        public final java.lang.String getName() {
            return this.name;
        }

        @Override
        public final java.lang.String getNamespace() {
            return this.namespace;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getAnnotations() != null) {
                data.set("annotations", om.valueToTree(this.getAnnotations()));
            }
            if (this.getFinalizers() != null) {
                data.set("finalizers", om.valueToTree(this.getFinalizers()));
            }
            if (this.getLabels() != null) {
                data.set("labels", om.valueToTree(this.getLabels()));
            }
            if (this.getName() != null) {
                data.set("name", om.valueToTree(this.getName()));
            }
            if (this.getNamespace() != null) {
                data.set("namespace", om.valueToTree(this.getNamespace()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ConfigurationSpecTemplateMetadata"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecTemplateMetadata.Jsii$Proxy that = (ConfigurationSpecTemplateMetadata.Jsii$Proxy) o;

            if (this.annotations != null ? !this.annotations.equals(that.annotations) : that.annotations != null) return false;
            if (this.finalizers != null ? !this.finalizers.equals(that.finalizers) : that.finalizers != null) return false;
            if (this.labels != null ? !this.labels.equals(that.labels) : that.labels != null) return false;
            if (this.name != null ? !this.name.equals(that.name) : that.name != null) return false;
            return this.namespace != null ? this.namespace.equals(that.namespace) : that.namespace == null;
        }

        @Override
        public final int hashCode() {
            int result = this.annotations != null ? this.annotations.hashCode() : 0;
            result = 31 * result + (this.finalizers != null ? this.finalizers.hashCode() : 0);
            result = 31 * result + (this.labels != null ? this.labels.hashCode() : 0);
            result = 31 * result + (this.name != null ? this.name.hashCode() : 0);
            result = 31 * result + (this.namespace != null ? this.namespace.hashCode() : 0);
            return result;
        }
    }
}
