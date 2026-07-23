package imports.io.dapr;

/**
 * NameValuePair is a name/value pair.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.449Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecTracingOtelHeaders")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecTracingOtelHeaders.Jsii$Proxy.class)
public interface ConfigurationSpecTracingOtelHeaders extends software.amazon.jsii.JsiiSerializable {

    /**
     * Name of the property.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getName();

    /**
     * EnvRef is the name of an environmental variable to read the value from.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getEnvRef() {
        return null;
    }

    /**
     * SecretKeyRef is the reference of a value in a secret store component.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecTracingOtelHeadersSecretKeyRef getSecretKeyRef() {
        return null;
    }

    /**
     * Value of the property, in plaintext.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Object getValue() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecTracingOtelHeaders}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecTracingOtelHeaders}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecTracingOtelHeaders> {
        java.lang.String name;
        java.lang.String envRef;
        imports.io.dapr.ConfigurationSpecTracingOtelHeadersSecretKeyRef secretKeyRef;
        java.lang.Object value;

        /**
         * Sets the value of {@link ConfigurationSpecTracingOtelHeaders#getName}
         * @param name Name of the property. This parameter is required.
         * @return {@code this}
         */
        public Builder name(java.lang.String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTracingOtelHeaders#getEnvRef}
         * @param envRef EnvRef is the name of an environmental variable to read the value from.
         * @return {@code this}
         */
        public Builder envRef(java.lang.String envRef) {
            this.envRef = envRef;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTracingOtelHeaders#getSecretKeyRef}
         * @param secretKeyRef SecretKeyRef is the reference of a value in a secret store component.
         * @return {@code this}
         */
        public Builder secretKeyRef(imports.io.dapr.ConfigurationSpecTracingOtelHeadersSecretKeyRef secretKeyRef) {
            this.secretKeyRef = secretKeyRef;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTracingOtelHeaders#getValue}
         * @param value Value of the property, in plaintext.
         * @return {@code this}
         */
        public Builder value(java.lang.Object value) {
            this.value = value;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecTracingOtelHeaders}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecTracingOtelHeaders build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecTracingOtelHeaders}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecTracingOtelHeaders {
        private final java.lang.String name;
        private final java.lang.String envRef;
        private final imports.io.dapr.ConfigurationSpecTracingOtelHeadersSecretKeyRef secretKeyRef;
        private final java.lang.Object value;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.envRef = software.amazon.jsii.Kernel.get(this, "envRef", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.secretKeyRef = software.amazon.jsii.Kernel.get(this, "secretKeyRef", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecTracingOtelHeadersSecretKeyRef.class));
            this.value = software.amazon.jsii.Kernel.get(this, "value", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.name = java.util.Objects.requireNonNull(builder.name, "name is required");
            this.envRef = builder.envRef;
            this.secretKeyRef = builder.secretKeyRef;
            this.value = builder.value;
        }

        @Override
        public final java.lang.String getName() {
            return this.name;
        }

        @Override
        public final java.lang.String getEnvRef() {
            return this.envRef;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecTracingOtelHeadersSecretKeyRef getSecretKeyRef() {
            return this.secretKeyRef;
        }

        @Override
        public final java.lang.Object getValue() {
            return this.value;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("name", om.valueToTree(this.getName()));
            if (this.getEnvRef() != null) {
                data.set("envRef", om.valueToTree(this.getEnvRef()));
            }
            if (this.getSecretKeyRef() != null) {
                data.set("secretKeyRef", om.valueToTree(this.getSecretKeyRef()));
            }
            if (this.getValue() != null) {
                data.set("value", om.valueToTree(this.getValue()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecTracingOtelHeaders"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecTracingOtelHeaders.Jsii$Proxy that = (ConfigurationSpecTracingOtelHeaders.Jsii$Proxy) o;

            if (!name.equals(that.name)) return false;
            if (this.envRef != null ? !this.envRef.equals(that.envRef) : that.envRef != null) return false;
            if (this.secretKeyRef != null ? !this.secretKeyRef.equals(that.secretKeyRef) : that.secretKeyRef != null) return false;
            return this.value != null ? this.value.equals(that.value) : that.value == null;
        }

        @Override
        public final int hashCode() {
            int result = this.name.hashCode();
            result = 31 * result + (this.envRef != null ? this.envRef.hashCode() : 0);
            result = 31 * result + (this.secretKeyRef != null ? this.secretKeyRef.hashCode() : 0);
            result = 31 * result + (this.value != null ? this.value.hashCode() : 0);
            return result;
        }
    }
}
