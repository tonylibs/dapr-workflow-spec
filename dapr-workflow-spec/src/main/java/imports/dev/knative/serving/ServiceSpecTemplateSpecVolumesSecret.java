package imports.dev.knative.serving;

/**
 * secret represents a secret that should populate this volume.
 * <p>
 * More info: https://kubernetes.io/docs/concepts/storage/volumes#secret
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.195Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ServiceSpecTemplateSpecVolumesSecret")
@software.amazon.jsii.Jsii.Proxy(ServiceSpecTemplateSpecVolumesSecret.Jsii$Proxy.class)
public interface ServiceSpecTemplateSpecVolumesSecret extends software.amazon.jsii.JsiiSerializable {

    /**
     * defaultMode is Optional: mode bits used to set permissions on created files by default.
     * <p>
     * Must be an octal value between 0000 and 0777 or a decimal value between 0 and 511.
     * YAML accepts both octal and decimal values, JSON requires decimal values
     * for mode bits. Defaults to 0644.
     * Directories within the path are not affected by this setting.
     * This might be in conflict with other options that affect the file
     * mode, like fsGroup, and the result can be other mode bits set.
     * <p>
     * Default: 0644.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getDefaultMode() {
        return null;
    }

    /**
     * items If unspecified, each key-value pair in the Data field of the referenced Secret will be projected into the volume as a file whose name is the key and content is the value.
     * <p>
     * If specified, the listed keys will be
     * projected into the specified paths, and unlisted keys will not be
     * present. If a key is specified which is not present in the Secret,
     * the volume setup will error unless it is marked optional. Paths must be
     * relative and may not contain the '..' path or start with '..'.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesSecretItems> getItems() {
        return null;
    }

    /**
     * optional field specify whether the Secret or its keys must be defined.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getOptional() {
        return null;
    }

    /**
     * secretName is the name of the secret in the pod's namespace to use.
     * <p>
     * More info: https://kubernetes.io/docs/concepts/storage/volumes#secret
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getSecretName() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ServiceSpecTemplateSpecVolumesSecret}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ServiceSpecTemplateSpecVolumesSecret}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ServiceSpecTemplateSpecVolumesSecret> {
        java.lang.Number defaultMode;
        java.util.List<imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesSecretItems> items;
        java.lang.Boolean optional;
        java.lang.String secretName;

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumesSecret#getDefaultMode}
         * @param defaultMode defaultMode is Optional: mode bits used to set permissions on created files by default.
         *                    Must be an octal value between 0000 and 0777 or a decimal value between 0 and 511.
         *                    YAML accepts both octal and decimal values, JSON requires decimal values
         *                    for mode bits. Defaults to 0644.
         *                    Directories within the path are not affected by this setting.
         *                    This might be in conflict with other options that affect the file
         *                    mode, like fsGroup, and the result can be other mode bits set.
         * @return {@code this}
         */
        public Builder defaultMode(java.lang.Number defaultMode) {
            this.defaultMode = defaultMode;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumesSecret#getItems}
         * @param items items If unspecified, each key-value pair in the Data field of the referenced Secret will be projected into the volume as a file whose name is the key and content is the value.
         *              If specified, the listed keys will be
         *              projected into the specified paths, and unlisted keys will not be
         *              present. If a key is specified which is not present in the Secret,
         *              the volume setup will error unless it is marked optional. Paths must be
         *              relative and may not contain the '..' path or start with '..'.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder items(java.util.List<? extends imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesSecretItems> items) {
            this.items = (java.util.List<imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesSecretItems>)items;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumesSecret#getOptional}
         * @param optional optional field specify whether the Secret or its keys must be defined.
         * @return {@code this}
         */
        public Builder optional(java.lang.Boolean optional) {
            this.optional = optional;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumesSecret#getSecretName}
         * @param secretName secretName is the name of the secret in the pod's namespace to use.
         *                   More info: https://kubernetes.io/docs/concepts/storage/volumes#secret
         * @return {@code this}
         */
        public Builder secretName(java.lang.String secretName) {
            this.secretName = secretName;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ServiceSpecTemplateSpecVolumesSecret}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ServiceSpecTemplateSpecVolumesSecret build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ServiceSpecTemplateSpecVolumesSecret}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ServiceSpecTemplateSpecVolumesSecret {
        private final java.lang.Number defaultMode;
        private final java.util.List<imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesSecretItems> items;
        private final java.lang.Boolean optional;
        private final java.lang.String secretName;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.defaultMode = software.amazon.jsii.Kernel.get(this, "defaultMode", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.items = software.amazon.jsii.Kernel.get(this, "items", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesSecretItems.class)));
            this.optional = software.amazon.jsii.Kernel.get(this, "optional", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.secretName = software.amazon.jsii.Kernel.get(this, "secretName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.defaultMode = builder.defaultMode;
            this.items = (java.util.List<imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesSecretItems>)builder.items;
            this.optional = builder.optional;
            this.secretName = builder.secretName;
        }

        @Override
        public final java.lang.Number getDefaultMode() {
            return this.defaultMode;
        }

        @Override
        public final java.util.List<imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesSecretItems> getItems() {
            return this.items;
        }

        @Override
        public final java.lang.Boolean getOptional() {
            return this.optional;
        }

        @Override
        public final java.lang.String getSecretName() {
            return this.secretName;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getDefaultMode() != null) {
                data.set("defaultMode", om.valueToTree(this.getDefaultMode()));
            }
            if (this.getItems() != null) {
                data.set("items", om.valueToTree(this.getItems()));
            }
            if (this.getOptional() != null) {
                data.set("optional", om.valueToTree(this.getOptional()));
            }
            if (this.getSecretName() != null) {
                data.set("secretName", om.valueToTree(this.getSecretName()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ServiceSpecTemplateSpecVolumesSecret"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ServiceSpecTemplateSpecVolumesSecret.Jsii$Proxy that = (ServiceSpecTemplateSpecVolumesSecret.Jsii$Proxy) o;

            if (this.defaultMode != null ? !this.defaultMode.equals(that.defaultMode) : that.defaultMode != null) return false;
            if (this.items != null ? !this.items.equals(that.items) : that.items != null) return false;
            if (this.optional != null ? !this.optional.equals(that.optional) : that.optional != null) return false;
            return this.secretName != null ? this.secretName.equals(that.secretName) : that.secretName == null;
        }

        @Override
        public final int hashCode() {
            int result = this.defaultMode != null ? this.defaultMode.hashCode() : 0;
            result = 31 * result + (this.items != null ? this.items.hashCode() : 0);
            result = 31 * result + (this.optional != null ? this.optional.hashCode() : 0);
            result = 31 * result + (this.secretName != null ? this.secretName.hashCode() : 0);
            return result;
        }
    }
}
