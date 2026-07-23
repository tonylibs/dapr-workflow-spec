package imports.io.dapr;

/**
 * ComponentSpec is the spec for a component.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.435Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ComponentSpec")
@software.amazon.jsii.Jsii.Proxy(ComponentSpec.Jsii$Proxy.class)
public interface ComponentSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.util.List<imports.io.dapr.ComponentSpecMetadata> getMetadata();

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getType();

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getVersion();

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getIgnoreErrors() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getInitTimeout() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ComponentSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ComponentSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ComponentSpec> {
        java.util.List<imports.io.dapr.ComponentSpecMetadata> metadata;
        java.lang.String type;
        java.lang.String version;
        java.lang.Boolean ignoreErrors;
        java.lang.String initTimeout;

        /**
         * Sets the value of {@link ComponentSpec#getMetadata}
         * @param metadata the value to be set. This parameter is required.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder metadata(java.util.List<? extends imports.io.dapr.ComponentSpecMetadata> metadata) {
            this.metadata = (java.util.List<imports.io.dapr.ComponentSpecMetadata>)metadata;
            return this;
        }

        /**
         * Sets the value of {@link ComponentSpec#getType}
         * @param type the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder type(java.lang.String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the value of {@link ComponentSpec#getVersion}
         * @param version the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder version(java.lang.String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the value of {@link ComponentSpec#getIgnoreErrors}
         * @param ignoreErrors the value to be set.
         * @return {@code this}
         */
        public Builder ignoreErrors(java.lang.Boolean ignoreErrors) {
            this.ignoreErrors = ignoreErrors;
            return this;
        }

        /**
         * Sets the value of {@link ComponentSpec#getInitTimeout}
         * @param initTimeout the value to be set.
         * @return {@code this}
         */
        public Builder initTimeout(java.lang.String initTimeout) {
            this.initTimeout = initTimeout;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ComponentSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ComponentSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ComponentSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ComponentSpec {
        private final java.util.List<imports.io.dapr.ComponentSpecMetadata> metadata;
        private final java.lang.String type;
        private final java.lang.String version;
        private final java.lang.Boolean ignoreErrors;
        private final java.lang.String initTimeout;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.metadata = software.amazon.jsii.Kernel.get(this, "metadata", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ComponentSpecMetadata.class)));
            this.type = software.amazon.jsii.Kernel.get(this, "type", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.version = software.amazon.jsii.Kernel.get(this, "version", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.ignoreErrors = software.amazon.jsii.Kernel.get(this, "ignoreErrors", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.initTimeout = software.amazon.jsii.Kernel.get(this, "initTimeout", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.metadata = (java.util.List<imports.io.dapr.ComponentSpecMetadata>)java.util.Objects.requireNonNull(builder.metadata, "metadata is required");
            this.type = java.util.Objects.requireNonNull(builder.type, "type is required");
            this.version = java.util.Objects.requireNonNull(builder.version, "version is required");
            this.ignoreErrors = builder.ignoreErrors;
            this.initTimeout = builder.initTimeout;
        }

        @Override
        public final java.util.List<imports.io.dapr.ComponentSpecMetadata> getMetadata() {
            return this.metadata;
        }

        @Override
        public final java.lang.String getType() {
            return this.type;
        }

        @Override
        public final java.lang.String getVersion() {
            return this.version;
        }

        @Override
        public final java.lang.Boolean getIgnoreErrors() {
            return this.ignoreErrors;
        }

        @Override
        public final java.lang.String getInitTimeout() {
            return this.initTimeout;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("metadata", om.valueToTree(this.getMetadata()));
            data.set("type", om.valueToTree(this.getType()));
            data.set("version", om.valueToTree(this.getVersion()));
            if (this.getIgnoreErrors() != null) {
                data.set("ignoreErrors", om.valueToTree(this.getIgnoreErrors()));
            }
            if (this.getInitTimeout() != null) {
                data.set("initTimeout", om.valueToTree(this.getInitTimeout()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ComponentSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ComponentSpec.Jsii$Proxy that = (ComponentSpec.Jsii$Proxy) o;

            if (!metadata.equals(that.metadata)) return false;
            if (!type.equals(that.type)) return false;
            if (!version.equals(that.version)) return false;
            if (this.ignoreErrors != null ? !this.ignoreErrors.equals(that.ignoreErrors) : that.ignoreErrors != null) return false;
            return this.initTimeout != null ? this.initTimeout.equals(that.initTimeout) : that.initTimeout == null;
        }

        @Override
        public final int hashCode() {
            int result = this.metadata.hashCode();
            result = 31 * result + (this.type.hashCode());
            result = 31 * result + (this.version.hashCode());
            result = 31 * result + (this.ignoreErrors != null ? this.ignoreErrors.hashCode() : 0);
            result = 31 * result + (this.initTimeout != null ? this.initTimeout.hashCode() : 0);
            return result;
        }
    }
}
