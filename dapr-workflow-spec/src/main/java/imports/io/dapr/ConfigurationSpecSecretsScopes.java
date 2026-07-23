package imports.io.dapr;

/**
 * SecretsScope defines the scope for secrets.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.449Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecSecretsScopes")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecSecretsScopes.Jsii$Proxy.class)
public interface ConfigurationSpecSecretsScopes extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getStoreName();

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getAllowedSecrets() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getDefaultAccess() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getDeniedSecrets() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecSecretsScopes}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecSecretsScopes}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecSecretsScopes> {
        java.lang.String storeName;
        java.util.List<java.lang.String> allowedSecrets;
        java.lang.String defaultAccess;
        java.util.List<java.lang.String> deniedSecrets;

        /**
         * Sets the value of {@link ConfigurationSpecSecretsScopes#getStoreName}
         * @param storeName the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder storeName(java.lang.String storeName) {
            this.storeName = storeName;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecSecretsScopes#getAllowedSecrets}
         * @param allowedSecrets the value to be set.
         * @return {@code this}
         */
        public Builder allowedSecrets(java.util.List<java.lang.String> allowedSecrets) {
            this.allowedSecrets = allowedSecrets;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecSecretsScopes#getDefaultAccess}
         * @param defaultAccess the value to be set.
         * @return {@code this}
         */
        public Builder defaultAccess(java.lang.String defaultAccess) {
            this.defaultAccess = defaultAccess;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecSecretsScopes#getDeniedSecrets}
         * @param deniedSecrets the value to be set.
         * @return {@code this}
         */
        public Builder deniedSecrets(java.util.List<java.lang.String> deniedSecrets) {
            this.deniedSecrets = deniedSecrets;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecSecretsScopes}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecSecretsScopes build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecSecretsScopes}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecSecretsScopes {
        private final java.lang.String storeName;
        private final java.util.List<java.lang.String> allowedSecrets;
        private final java.lang.String defaultAccess;
        private final java.util.List<java.lang.String> deniedSecrets;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.storeName = software.amazon.jsii.Kernel.get(this, "storeName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.allowedSecrets = software.amazon.jsii.Kernel.get(this, "allowedSecrets", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.defaultAccess = software.amazon.jsii.Kernel.get(this, "defaultAccess", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.deniedSecrets = software.amazon.jsii.Kernel.get(this, "deniedSecrets", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.storeName = java.util.Objects.requireNonNull(builder.storeName, "storeName is required");
            this.allowedSecrets = builder.allowedSecrets;
            this.defaultAccess = builder.defaultAccess;
            this.deniedSecrets = builder.deniedSecrets;
        }

        @Override
        public final java.lang.String getStoreName() {
            return this.storeName;
        }

        @Override
        public final java.util.List<java.lang.String> getAllowedSecrets() {
            return this.allowedSecrets;
        }

        @Override
        public final java.lang.String getDefaultAccess() {
            return this.defaultAccess;
        }

        @Override
        public final java.util.List<java.lang.String> getDeniedSecrets() {
            return this.deniedSecrets;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("storeName", om.valueToTree(this.getStoreName()));
            if (this.getAllowedSecrets() != null) {
                data.set("allowedSecrets", om.valueToTree(this.getAllowedSecrets()));
            }
            if (this.getDefaultAccess() != null) {
                data.set("defaultAccess", om.valueToTree(this.getDefaultAccess()));
            }
            if (this.getDeniedSecrets() != null) {
                data.set("deniedSecrets", om.valueToTree(this.getDeniedSecrets()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecSecretsScopes"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecSecretsScopes.Jsii$Proxy that = (ConfigurationSpecSecretsScopes.Jsii$Proxy) o;

            if (!storeName.equals(that.storeName)) return false;
            if (this.allowedSecrets != null ? !this.allowedSecrets.equals(that.allowedSecrets) : that.allowedSecrets != null) return false;
            if (this.defaultAccess != null ? !this.defaultAccess.equals(that.defaultAccess) : that.defaultAccess != null) return false;
            return this.deniedSecrets != null ? this.deniedSecrets.equals(that.deniedSecrets) : that.deniedSecrets == null;
        }

        @Override
        public final int hashCode() {
            int result = this.storeName.hashCode();
            result = 31 * result + (this.allowedSecrets != null ? this.allowedSecrets.hashCode() : 0);
            result = 31 * result + (this.defaultAccess != null ? this.defaultAccess.hashCode() : 0);
            result = 31 * result + (this.deniedSecrets != null ? this.deniedSecrets.hashCode() : 0);
            return result;
        }
    }
}
