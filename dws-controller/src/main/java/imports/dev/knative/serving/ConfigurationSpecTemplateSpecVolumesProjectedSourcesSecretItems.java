package imports.dev.knative.serving;

/**
 * Maps a string key to a path within a volume.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.141Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems.Jsii$Proxy.class)
public interface ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems extends software.amazon.jsii.JsiiSerializable {

    /**
     * key is the key to project.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getKey();

    /**
     * path is the relative path of the file to map the key to.
     * <p>
     * May not be an absolute path.
     * May not contain the path element '..'.
     * May not start with the string '..'.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getPath();

    /**
     * mode is Optional: mode bits used to set permissions on this file.
     * <p>
     * Must be an octal value between 0000 and 0777 or a decimal value between 0 and 511.
     * YAML accepts both octal and decimal values, JSON requires decimal values for mode bits.
     * If not specified, the volume defaultMode will be used.
     * This might be in conflict with other options that affect the file
     * mode, like fsGroup, and the result can be other mode bits set.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getMode() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems> {
        java.lang.String key;
        java.lang.String path;
        java.lang.Number mode;

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems#getKey}
         * @param key key is the key to project. This parameter is required.
         * @return {@code this}
         */
        public Builder key(java.lang.String key) {
            this.key = key;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems#getPath}
         * @param path path is the relative path of the file to map the key to. This parameter is required.
         *             May not be an absolute path.
         *             May not contain the path element '..'.
         *             May not start with the string '..'.
         * @return {@code this}
         */
        public Builder path(java.lang.String path) {
            this.path = path;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems#getMode}
         * @param mode mode is Optional: mode bits used to set permissions on this file.
         *             Must be an octal value between 0000 and 0777 or a decimal value between 0 and 511.
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
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems {
        private final java.lang.String key;
        private final java.lang.String path;
        private final java.lang.Number mode;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.key = software.amazon.jsii.Kernel.get(this, "key", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.path = software.amazon.jsii.Kernel.get(this, "path", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.mode = software.amazon.jsii.Kernel.get(this, "mode", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.key = java.util.Objects.requireNonNull(builder.key, "key is required");
            this.path = java.util.Objects.requireNonNull(builder.path, "path is required");
            this.mode = builder.mode;
        }

        @Override
        public final java.lang.String getKey() {
            return this.key;
        }

        @Override
        public final java.lang.String getPath() {
            return this.path;
        }

        @Override
        public final java.lang.Number getMode() {
            return this.mode;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("key", om.valueToTree(this.getKey()));
            data.set("path", om.valueToTree(this.getPath()));
            if (this.getMode() != null) {
                data.set("mode", om.valueToTree(this.getMode()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems.Jsii$Proxy that = (ConfigurationSpecTemplateSpecVolumesProjectedSourcesSecretItems.Jsii$Proxy) o;

            if (!key.equals(that.key)) return false;
            if (!path.equals(that.path)) return false;
            return this.mode != null ? this.mode.equals(that.mode) : that.mode == null;
        }

        @Override
        public final int hashCode() {
            int result = this.key.hashCode();
            result = 31 * result + (this.path.hashCode());
            result = 31 * result + (this.mode != null ? this.mode.hashCode() : 0);
            return result;
        }
    }
}
