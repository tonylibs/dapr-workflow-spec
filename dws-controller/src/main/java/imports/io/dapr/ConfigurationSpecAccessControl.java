package imports.io.dapr;

/**
 * AccessControlSpec is the spec object in ConfigurationSpec.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.438Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecAccessControl")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecAccessControl.Jsii$Proxy.class)
public interface ConfigurationSpecAccessControl extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getDefaultAction() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.ConfigurationSpecAccessControlPolicies> getPolicies() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getTrustDomain() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecAccessControl}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecAccessControl}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecAccessControl> {
        java.lang.String defaultAction;
        java.util.List<imports.io.dapr.ConfigurationSpecAccessControlPolicies> policies;
        java.lang.String trustDomain;

        /**
         * Sets the value of {@link ConfigurationSpecAccessControl#getDefaultAction}
         * @param defaultAction the value to be set.
         * @return {@code this}
         */
        public Builder defaultAction(java.lang.String defaultAction) {
            this.defaultAction = defaultAction;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecAccessControl#getPolicies}
         * @param policies the value to be set.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder policies(java.util.List<? extends imports.io.dapr.ConfigurationSpecAccessControlPolicies> policies) {
            this.policies = (java.util.List<imports.io.dapr.ConfigurationSpecAccessControlPolicies>)policies;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecAccessControl#getTrustDomain}
         * @param trustDomain the value to be set.
         * @return {@code this}
         */
        public Builder trustDomain(java.lang.String trustDomain) {
            this.trustDomain = trustDomain;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecAccessControl}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecAccessControl build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecAccessControl}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecAccessControl {
        private final java.lang.String defaultAction;
        private final java.util.List<imports.io.dapr.ConfigurationSpecAccessControlPolicies> policies;
        private final java.lang.String trustDomain;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.defaultAction = software.amazon.jsii.Kernel.get(this, "defaultAction", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.policies = software.amazon.jsii.Kernel.get(this, "policies", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecAccessControlPolicies.class)));
            this.trustDomain = software.amazon.jsii.Kernel.get(this, "trustDomain", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.defaultAction = builder.defaultAction;
            this.policies = (java.util.List<imports.io.dapr.ConfigurationSpecAccessControlPolicies>)builder.policies;
            this.trustDomain = builder.trustDomain;
        }

        @Override
        public final java.lang.String getDefaultAction() {
            return this.defaultAction;
        }

        @Override
        public final java.util.List<imports.io.dapr.ConfigurationSpecAccessControlPolicies> getPolicies() {
            return this.policies;
        }

        @Override
        public final java.lang.String getTrustDomain() {
            return this.trustDomain;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getDefaultAction() != null) {
                data.set("defaultAction", om.valueToTree(this.getDefaultAction()));
            }
            if (this.getPolicies() != null) {
                data.set("policies", om.valueToTree(this.getPolicies()));
            }
            if (this.getTrustDomain() != null) {
                data.set("trustDomain", om.valueToTree(this.getTrustDomain()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecAccessControl"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecAccessControl.Jsii$Proxy that = (ConfigurationSpecAccessControl.Jsii$Proxy) o;

            if (this.defaultAction != null ? !this.defaultAction.equals(that.defaultAction) : that.defaultAction != null) return false;
            if (this.policies != null ? !this.policies.equals(that.policies) : that.policies != null) return false;
            return this.trustDomain != null ? this.trustDomain.equals(that.trustDomain) : that.trustDomain == null;
        }

        @Override
        public final int hashCode() {
            int result = this.defaultAction != null ? this.defaultAction.hashCode() : 0;
            result = 31 * result + (this.policies != null ? this.policies.hashCode() : 0);
            result = 31 * result + (this.trustDomain != null ? this.trustDomain.hashCode() : 0);
            return result;
        }
    }
}
