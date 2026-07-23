package imports.io.dapr;

/**
 * AppPolicySpec defines the policy data structure for each app.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.438Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecAccessControlPolicies")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecAccessControlPolicies.Jsii$Proxy.class)
public interface ConfigurationSpecAccessControlPolicies extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getAppId();

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getDefaultAction() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getNamespace() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.ConfigurationSpecAccessControlPoliciesOperations> getOperations() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getTrustDomain() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecAccessControlPolicies}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecAccessControlPolicies}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecAccessControlPolicies> {
        java.lang.String appId;
        java.lang.String defaultAction;
        java.lang.String namespace;
        java.util.List<imports.io.dapr.ConfigurationSpecAccessControlPoliciesOperations> operations;
        java.lang.String trustDomain;

        /**
         * Sets the value of {@link ConfigurationSpecAccessControlPolicies#getAppId}
         * @param appId the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder appId(java.lang.String appId) {
            this.appId = appId;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecAccessControlPolicies#getDefaultAction}
         * @param defaultAction the value to be set.
         * @return {@code this}
         */
        public Builder defaultAction(java.lang.String defaultAction) {
            this.defaultAction = defaultAction;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecAccessControlPolicies#getNamespace}
         * @param namespace the value to be set.
         * @return {@code this}
         */
        public Builder namespace(java.lang.String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecAccessControlPolicies#getOperations}
         * @param operations the value to be set.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder operations(java.util.List<? extends imports.io.dapr.ConfigurationSpecAccessControlPoliciesOperations> operations) {
            this.operations = (java.util.List<imports.io.dapr.ConfigurationSpecAccessControlPoliciesOperations>)operations;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecAccessControlPolicies#getTrustDomain}
         * @param trustDomain the value to be set.
         * @return {@code this}
         */
        public Builder trustDomain(java.lang.String trustDomain) {
            this.trustDomain = trustDomain;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecAccessControlPolicies}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecAccessControlPolicies build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecAccessControlPolicies}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecAccessControlPolicies {
        private final java.lang.String appId;
        private final java.lang.String defaultAction;
        private final java.lang.String namespace;
        private final java.util.List<imports.io.dapr.ConfigurationSpecAccessControlPoliciesOperations> operations;
        private final java.lang.String trustDomain;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.appId = software.amazon.jsii.Kernel.get(this, "appId", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.defaultAction = software.amazon.jsii.Kernel.get(this, "defaultAction", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.namespace = software.amazon.jsii.Kernel.get(this, "namespace", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.operations = software.amazon.jsii.Kernel.get(this, "operations", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecAccessControlPoliciesOperations.class)));
            this.trustDomain = software.amazon.jsii.Kernel.get(this, "trustDomain", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.appId = java.util.Objects.requireNonNull(builder.appId, "appId is required");
            this.defaultAction = builder.defaultAction;
            this.namespace = builder.namespace;
            this.operations = (java.util.List<imports.io.dapr.ConfigurationSpecAccessControlPoliciesOperations>)builder.operations;
            this.trustDomain = builder.trustDomain;
        }

        @Override
        public final java.lang.String getAppId() {
            return this.appId;
        }

        @Override
        public final java.lang.String getDefaultAction() {
            return this.defaultAction;
        }

        @Override
        public final java.lang.String getNamespace() {
            return this.namespace;
        }

        @Override
        public final java.util.List<imports.io.dapr.ConfigurationSpecAccessControlPoliciesOperations> getOperations() {
            return this.operations;
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

            data.set("appId", om.valueToTree(this.getAppId()));
            if (this.getDefaultAction() != null) {
                data.set("defaultAction", om.valueToTree(this.getDefaultAction()));
            }
            if (this.getNamespace() != null) {
                data.set("namespace", om.valueToTree(this.getNamespace()));
            }
            if (this.getOperations() != null) {
                data.set("operations", om.valueToTree(this.getOperations()));
            }
            if (this.getTrustDomain() != null) {
                data.set("trustDomain", om.valueToTree(this.getTrustDomain()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecAccessControlPolicies"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecAccessControlPolicies.Jsii$Proxy that = (ConfigurationSpecAccessControlPolicies.Jsii$Proxy) o;

            if (!appId.equals(that.appId)) return false;
            if (this.defaultAction != null ? !this.defaultAction.equals(that.defaultAction) : that.defaultAction != null) return false;
            if (this.namespace != null ? !this.namespace.equals(that.namespace) : that.namespace != null) return false;
            if (this.operations != null ? !this.operations.equals(that.operations) : that.operations != null) return false;
            return this.trustDomain != null ? this.trustDomain.equals(that.trustDomain) : that.trustDomain == null;
        }

        @Override
        public final int hashCode() {
            int result = this.appId.hashCode();
            result = 31 * result + (this.defaultAction != null ? this.defaultAction.hashCode() : 0);
            result = 31 * result + (this.namespace != null ? this.namespace.hashCode() : 0);
            result = 31 * result + (this.operations != null ? this.operations.hashCode() : 0);
            result = 31 * result + (this.trustDomain != null ? this.trustDomain.hashCode() : 0);
            return result;
        }
    }
}
