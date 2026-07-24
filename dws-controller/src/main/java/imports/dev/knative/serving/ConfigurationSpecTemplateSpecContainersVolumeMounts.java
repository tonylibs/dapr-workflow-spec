package imports.dev.knative.serving;

/**
 * VolumeMount describes a mounting of a Volume within a container.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.134Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ConfigurationSpecTemplateSpecContainersVolumeMounts")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecTemplateSpecContainersVolumeMounts.Jsii$Proxy.class)
public interface ConfigurationSpecTemplateSpecContainersVolumeMounts extends software.amazon.jsii.JsiiSerializable {

    /**
     * Path within the container at which the volume should be mounted.
     * <p>
     * Must
     * not contain ':'.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getMountPath();

    /**
     * This must match the Name of a Volume.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getName();

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-volumes-mount-propagation.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getMountPropagation() {
        return null;
    }

    /**
     * Mounted read-only if true, read-write otherwise (false or unspecified).
     * <p>
     * Defaults to false.
     * <p>
     * Default: false.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getReadOnly() {
        return null;
    }

    /**
     * Path within the volume from which the container's volume should be mounted.
     * <p>
     * Defaults to "" (volume's root).
     * <p>
     * Default: volume's root).
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getSubPath() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecTemplateSpecContainersVolumeMounts}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecTemplateSpecContainersVolumeMounts}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecTemplateSpecContainersVolumeMounts> {
        java.lang.String mountPath;
        java.lang.String name;
        java.lang.String mountPropagation;
        java.lang.Boolean readOnly;
        java.lang.String subPath;

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersVolumeMounts#getMountPath}
         * @param mountPath Path within the container at which the volume should be mounted. This parameter is required.
         *                  Must
         *                  not contain ':'.
         * @return {@code this}
         */
        public Builder mountPath(java.lang.String mountPath) {
            this.mountPath = mountPath;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersVolumeMounts#getName}
         * @param name This must match the Name of a Volume. This parameter is required.
         * @return {@code this}
         */
        public Builder name(java.lang.String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersVolumeMounts#getMountPropagation}
         * @param mountPropagation This is accessible behind a feature flag - kubernetes.podspec-volumes-mount-propagation.
         * @return {@code this}
         */
        public Builder mountPropagation(java.lang.String mountPropagation) {
            this.mountPropagation = mountPropagation;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersVolumeMounts#getReadOnly}
         * @param readOnly Mounted read-only if true, read-write otherwise (false or unspecified).
         *                 Defaults to false.
         * @return {@code this}
         */
        public Builder readOnly(java.lang.Boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersVolumeMounts#getSubPath}
         * @param subPath Path within the volume from which the container's volume should be mounted.
         *                Defaults to "" (volume's root).
         * @return {@code this}
         */
        public Builder subPath(java.lang.String subPath) {
            this.subPath = subPath;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecTemplateSpecContainersVolumeMounts}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecTemplateSpecContainersVolumeMounts build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecTemplateSpecContainersVolumeMounts}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecTemplateSpecContainersVolumeMounts {
        private final java.lang.String mountPath;
        private final java.lang.String name;
        private final java.lang.String mountPropagation;
        private final java.lang.Boolean readOnly;
        private final java.lang.String subPath;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.mountPath = software.amazon.jsii.Kernel.get(this, "mountPath", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.mountPropagation = software.amazon.jsii.Kernel.get(this, "mountPropagation", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.readOnly = software.amazon.jsii.Kernel.get(this, "readOnly", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.subPath = software.amazon.jsii.Kernel.get(this, "subPath", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.mountPath = java.util.Objects.requireNonNull(builder.mountPath, "mountPath is required");
            this.name = java.util.Objects.requireNonNull(builder.name, "name is required");
            this.mountPropagation = builder.mountPropagation;
            this.readOnly = builder.readOnly;
            this.subPath = builder.subPath;
        }

        @Override
        public final java.lang.String getMountPath() {
            return this.mountPath;
        }

        @Override
        public final java.lang.String getName() {
            return this.name;
        }

        @Override
        public final java.lang.String getMountPropagation() {
            return this.mountPropagation;
        }

        @Override
        public final java.lang.Boolean getReadOnly() {
            return this.readOnly;
        }

        @Override
        public final java.lang.String getSubPath() {
            return this.subPath;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("mountPath", om.valueToTree(this.getMountPath()));
            data.set("name", om.valueToTree(this.getName()));
            if (this.getMountPropagation() != null) {
                data.set("mountPropagation", om.valueToTree(this.getMountPropagation()));
            }
            if (this.getReadOnly() != null) {
                data.set("readOnly", om.valueToTree(this.getReadOnly()));
            }
            if (this.getSubPath() != null) {
                data.set("subPath", om.valueToTree(this.getSubPath()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ConfigurationSpecTemplateSpecContainersVolumeMounts"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecTemplateSpecContainersVolumeMounts.Jsii$Proxy that = (ConfigurationSpecTemplateSpecContainersVolumeMounts.Jsii$Proxy) o;

            if (!mountPath.equals(that.mountPath)) return false;
            if (!name.equals(that.name)) return false;
            if (this.mountPropagation != null ? !this.mountPropagation.equals(that.mountPropagation) : that.mountPropagation != null) return false;
            if (this.readOnly != null ? !this.readOnly.equals(that.readOnly) : that.readOnly != null) return false;
            return this.subPath != null ? this.subPath.equals(that.subPath) : that.subPath == null;
        }

        @Override
        public final int hashCode() {
            int result = this.mountPath.hashCode();
            result = 31 * result + (this.name.hashCode());
            result = 31 * result + (this.mountPropagation != null ? this.mountPropagation.hashCode() : 0);
            result = 31 * result + (this.readOnly != null ? this.readOnly.hashCode() : 0);
            result = 31 * result + (this.subPath != null ? this.subPath.hashCode() : 0);
            return result;
        }
    }
}
