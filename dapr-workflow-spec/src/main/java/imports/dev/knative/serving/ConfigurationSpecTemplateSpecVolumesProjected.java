package imports.dev.knative.serving;

/**
 * projected items for all in one resources secrets, configmaps, and downward API.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.139Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ConfigurationSpecTemplateSpecVolumesProjected")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecTemplateSpecVolumesProjected.Jsii$Proxy.class)
public interface ConfigurationSpecTemplateSpecVolumesProjected extends software.amazon.jsii.JsiiSerializable {

    /**
     * defaultMode are the mode bits used to set permissions on created files by default.
     * <p>
     * Must be an octal value between 0000 and 0777 or a decimal value between 0 and 511.
     * YAML accepts both octal and decimal values, JSON requires decimal values for mode bits.
     * Directories within the path are not affected by this setting.
     * This might be in conflict with other options that affect the file
     * mode, like fsGroup, and the result can be other mode bits set.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getDefaultMode() {
        return null;
    }

    /**
     * sources is the list of volume projections.
     * <p>
     * Each entry in this list
     * handles one source.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumesProjectedSources> getSources() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecTemplateSpecVolumesProjected}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecTemplateSpecVolumesProjected}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecTemplateSpecVolumesProjected> {
        java.lang.Number defaultMode;
        java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumesProjectedSources> sources;

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecVolumesProjected#getDefaultMode}
         * @param defaultMode defaultMode are the mode bits used to set permissions on created files by default.
         *                    Must be an octal value between 0000 and 0777 or a decimal value between 0 and 511.
         *                    YAML accepts both octal and decimal values, JSON requires decimal values for mode bits.
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
         * Sets the value of {@link ConfigurationSpecTemplateSpecVolumesProjected#getSources}
         * @param sources sources is the list of volume projections.
         *                Each entry in this list
         *                handles one source.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder sources(java.util.List<? extends imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumesProjectedSources> sources) {
            this.sources = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumesProjectedSources>)sources;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecTemplateSpecVolumesProjected}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecTemplateSpecVolumesProjected build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecTemplateSpecVolumesProjected}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecTemplateSpecVolumesProjected {
        private final java.lang.Number defaultMode;
        private final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumesProjectedSources> sources;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.defaultMode = software.amazon.jsii.Kernel.get(this, "defaultMode", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.sources = software.amazon.jsii.Kernel.get(this, "sources", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumesProjectedSources.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.defaultMode = builder.defaultMode;
            this.sources = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumesProjectedSources>)builder.sources;
        }

        @Override
        public final java.lang.Number getDefaultMode() {
            return this.defaultMode;
        }

        @Override
        public final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumesProjectedSources> getSources() {
            return this.sources;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getDefaultMode() != null) {
                data.set("defaultMode", om.valueToTree(this.getDefaultMode()));
            }
            if (this.getSources() != null) {
                data.set("sources", om.valueToTree(this.getSources()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ConfigurationSpecTemplateSpecVolumesProjected"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecTemplateSpecVolumesProjected.Jsii$Proxy that = (ConfigurationSpecTemplateSpecVolumesProjected.Jsii$Proxy) o;

            if (this.defaultMode != null ? !this.defaultMode.equals(that.defaultMode) : that.defaultMode != null) return false;
            return this.sources != null ? this.sources.equals(that.sources) : that.sources == null;
        }

        @Override
        public final int hashCode() {
            int result = this.defaultMode != null ? this.defaultMode.hashCode() : 0;
            result = 31 * result + (this.sources != null ? this.sources.hashCode() : 0);
            return result;
        }
    }
}
