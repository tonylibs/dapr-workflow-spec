package imports.dev.knative.serving;

/**
 * configMap represents a configMap that should populate this volume.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.192Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ServiceSpecTemplateSpecVolumesConfigMap")
@software.amazon.jsii.Jsii.Proxy(ServiceSpecTemplateSpecVolumesConfigMap.Jsii$Proxy.class)
public interface ServiceSpecTemplateSpecVolumesConfigMap extends software.amazon.jsii.JsiiSerializable {

    /**
     * defaultMode is optional: mode bits used to set permissions on created files by default.
     * <p>
     * Must be an octal value between 0000 and 0777 or a decimal value between 0 and 511.
     * YAML accepts both octal and decimal values, JSON requires decimal values for mode bits.
     * Defaults to 0644.
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
     * items if unspecified, each key-value pair in the Data field of the referenced ConfigMap will be projected into the volume as a file whose name is the key and content is the value.
     * <p>
     * If specified, the listed keys will be
     * projected into the specified paths, and unlisted keys will not be
     * present. If a key is specified which is not present in the ConfigMap,
     * the volume setup will error unless it is marked optional. Paths must be
     * relative and may not contain the '..' path or start with '..'.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesConfigMapItems> getItems() {
        return null;
    }

    /**
     * Name of the referent.
     * <p>
     * This field is effectively required, but due to backwards compatibility is
     * allowed to be empty. Instances of this type with an empty value here are
     * almost certainly wrong.
     * More info: https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#names
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getName() {
        return null;
    }

    /**
     * optional specify whether the ConfigMap or its keys must be defined.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getOptional() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ServiceSpecTemplateSpecVolumesConfigMap}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ServiceSpecTemplateSpecVolumesConfigMap}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ServiceSpecTemplateSpecVolumesConfigMap> {
        java.lang.Number defaultMode;
        java.util.List<imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesConfigMapItems> items;
        java.lang.String name;
        java.lang.Boolean optional;

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumesConfigMap#getDefaultMode}
         * @param defaultMode defaultMode is optional: mode bits used to set permissions on created files by default.
         *                    Must be an octal value between 0000 and 0777 or a decimal value between 0 and 511.
         *                    YAML accepts both octal and decimal values, JSON requires decimal values for mode bits.
         *                    Defaults to 0644.
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
         * Sets the value of {@link ServiceSpecTemplateSpecVolumesConfigMap#getItems}
         * @param items items if unspecified, each key-value pair in the Data field of the referenced ConfigMap will be projected into the volume as a file whose name is the key and content is the value.
         *              If specified, the listed keys will be
         *              projected into the specified paths, and unlisted keys will not be
         *              present. If a key is specified which is not present in the ConfigMap,
         *              the volume setup will error unless it is marked optional. Paths must be
         *              relative and may not contain the '..' path or start with '..'.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder items(java.util.List<? extends imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesConfigMapItems> items) {
            this.items = (java.util.List<imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesConfigMapItems>)items;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumesConfigMap#getName}
         * @param name Name of the referent.
         *             This field is effectively required, but due to backwards compatibility is
         *             allowed to be empty. Instances of this type with an empty value here are
         *             almost certainly wrong.
         *             More info: https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#names
         * @return {@code this}
         */
        public Builder name(java.lang.String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumesConfigMap#getOptional}
         * @param optional optional specify whether the ConfigMap or its keys must be defined.
         * @return {@code this}
         */
        public Builder optional(java.lang.Boolean optional) {
            this.optional = optional;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ServiceSpecTemplateSpecVolumesConfigMap}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ServiceSpecTemplateSpecVolumesConfigMap build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ServiceSpecTemplateSpecVolumesConfigMap}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ServiceSpecTemplateSpecVolumesConfigMap {
        private final java.lang.Number defaultMode;
        private final java.util.List<imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesConfigMapItems> items;
        private final java.lang.String name;
        private final java.lang.Boolean optional;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.defaultMode = software.amazon.jsii.Kernel.get(this, "defaultMode", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.items = software.amazon.jsii.Kernel.get(this, "items", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesConfigMapItems.class)));
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.optional = software.amazon.jsii.Kernel.get(this, "optional", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.defaultMode = builder.defaultMode;
            this.items = (java.util.List<imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesConfigMapItems>)builder.items;
            this.name = builder.name;
            this.optional = builder.optional;
        }

        @Override
        public final java.lang.Number getDefaultMode() {
            return this.defaultMode;
        }

        @Override
        public final java.util.List<imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesConfigMapItems> getItems() {
            return this.items;
        }

        @Override
        public final java.lang.String getName() {
            return this.name;
        }

        @Override
        public final java.lang.Boolean getOptional() {
            return this.optional;
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
            if (this.getName() != null) {
                data.set("name", om.valueToTree(this.getName()));
            }
            if (this.getOptional() != null) {
                data.set("optional", om.valueToTree(this.getOptional()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ServiceSpecTemplateSpecVolumesConfigMap"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ServiceSpecTemplateSpecVolumesConfigMap.Jsii$Proxy that = (ServiceSpecTemplateSpecVolumesConfigMap.Jsii$Proxy) o;

            if (this.defaultMode != null ? !this.defaultMode.equals(that.defaultMode) : that.defaultMode != null) return false;
            if (this.items != null ? !this.items.equals(that.items) : that.items != null) return false;
            if (this.name != null ? !this.name.equals(that.name) : that.name != null) return false;
            return this.optional != null ? this.optional.equals(that.optional) : that.optional == null;
        }

        @Override
        public final int hashCode() {
            int result = this.defaultMode != null ? this.defaultMode.hashCode() : 0;
            result = 31 * result + (this.items != null ? this.items.hashCode() : 0);
            result = 31 * result + (this.name != null ? this.name.hashCode() : 0);
            result = 31 * result + (this.optional != null ? this.optional.hashCode() : 0);
            return result;
        }
    }
}
