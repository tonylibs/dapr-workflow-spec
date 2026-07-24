package imports.io.dapr;

/**
 * APISpec describes the configuration for Dapr APIs.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.438Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecApi")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecApi.Jsii$Proxy.class)
public interface ConfigurationSpecApi extends software.amazon.jsii.JsiiSerializable {

    /**
     * List of allowed APIs.
     * <p>
     * Can be used in conjunction with denied.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.ConfigurationSpecApiAllowed> getAllowed() {
        return null;
    }

    /**
     * List of denied APIs.
     * <p>
     * Can be used in conjunction with allowed.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.ConfigurationSpecApiDenied> getDenied() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecApi}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecApi}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecApi> {
        java.util.List<imports.io.dapr.ConfigurationSpecApiAllowed> allowed;
        java.util.List<imports.io.dapr.ConfigurationSpecApiDenied> denied;

        /**
         * Sets the value of {@link ConfigurationSpecApi#getAllowed}
         * @param allowed List of allowed APIs.
         *                Can be used in conjunction with denied.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder allowed(java.util.List<? extends imports.io.dapr.ConfigurationSpecApiAllowed> allowed) {
            this.allowed = (java.util.List<imports.io.dapr.ConfigurationSpecApiAllowed>)allowed;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecApi#getDenied}
         * @param denied List of denied APIs.
         *               Can be used in conjunction with allowed.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder denied(java.util.List<? extends imports.io.dapr.ConfigurationSpecApiDenied> denied) {
            this.denied = (java.util.List<imports.io.dapr.ConfigurationSpecApiDenied>)denied;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecApi}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecApi build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecApi}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecApi {
        private final java.util.List<imports.io.dapr.ConfigurationSpecApiAllowed> allowed;
        private final java.util.List<imports.io.dapr.ConfigurationSpecApiDenied> denied;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.allowed = software.amazon.jsii.Kernel.get(this, "allowed", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecApiAllowed.class)));
            this.denied = software.amazon.jsii.Kernel.get(this, "denied", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecApiDenied.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.allowed = (java.util.List<imports.io.dapr.ConfigurationSpecApiAllowed>)builder.allowed;
            this.denied = (java.util.List<imports.io.dapr.ConfigurationSpecApiDenied>)builder.denied;
        }

        @Override
        public final java.util.List<imports.io.dapr.ConfigurationSpecApiAllowed> getAllowed() {
            return this.allowed;
        }

        @Override
        public final java.util.List<imports.io.dapr.ConfigurationSpecApiDenied> getDenied() {
            return this.denied;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getAllowed() != null) {
                data.set("allowed", om.valueToTree(this.getAllowed()));
            }
            if (this.getDenied() != null) {
                data.set("denied", om.valueToTree(this.getDenied()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecApi"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecApi.Jsii$Proxy that = (ConfigurationSpecApi.Jsii$Proxy) o;

            if (this.allowed != null ? !this.allowed.equals(that.allowed) : that.allowed != null) return false;
            return this.denied != null ? this.denied.equals(that.denied) : that.denied == null;
        }

        @Override
        public final int hashCode() {
            int result = this.allowed != null ? this.allowed.hashCode() : 0;
            result = 31 * result + (this.denied != null ? this.denied.hashCode() : 0);
            return result;
        }
    }
}
