package imports.io.dapr;

/**
 * MTLSSpec defines mTLS configuration.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.448Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecMtls")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecMtls.Jsii$Proxy.class)
public interface ConfigurationSpecMtls extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getControlPlaneTrustDomain();

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.Boolean getEnabled();

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getSentryAddress();

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getAllowedClockSkew() {
        return null;
    }

    /**
     * Additional token validators to use.
     * <p>
     * When Dapr is running in Kubernetes mode, this is in addition to the built-in "kubernetes" validator.
     * In self-hosted mode, enabling a custom validator will disable the built-in "insecure" validator.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.ConfigurationSpecMtlsTokenValidators> getTokenValidators() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getWorkloadCertTtl() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecMtls}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecMtls}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecMtls> {
        java.lang.String controlPlaneTrustDomain;
        java.lang.Boolean enabled;
        java.lang.String sentryAddress;
        java.lang.String allowedClockSkew;
        java.util.List<imports.io.dapr.ConfigurationSpecMtlsTokenValidators> tokenValidators;
        java.lang.String workloadCertTtl;

        /**
         * Sets the value of {@link ConfigurationSpecMtls#getControlPlaneTrustDomain}
         * @param controlPlaneTrustDomain the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder controlPlaneTrustDomain(java.lang.String controlPlaneTrustDomain) {
            this.controlPlaneTrustDomain = controlPlaneTrustDomain;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecMtls#getEnabled}
         * @param enabled the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder enabled(java.lang.Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecMtls#getSentryAddress}
         * @param sentryAddress the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder sentryAddress(java.lang.String sentryAddress) {
            this.sentryAddress = sentryAddress;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecMtls#getAllowedClockSkew}
         * @param allowedClockSkew the value to be set.
         * @return {@code this}
         */
        public Builder allowedClockSkew(java.lang.String allowedClockSkew) {
            this.allowedClockSkew = allowedClockSkew;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecMtls#getTokenValidators}
         * @param tokenValidators Additional token validators to use.
         *                        When Dapr is running in Kubernetes mode, this is in addition to the built-in "kubernetes" validator.
         *                        In self-hosted mode, enabling a custom validator will disable the built-in "insecure" validator.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder tokenValidators(java.util.List<? extends imports.io.dapr.ConfigurationSpecMtlsTokenValidators> tokenValidators) {
            this.tokenValidators = (java.util.List<imports.io.dapr.ConfigurationSpecMtlsTokenValidators>)tokenValidators;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecMtls#getWorkloadCertTtl}
         * @param workloadCertTtl the value to be set.
         * @return {@code this}
         */
        public Builder workloadCertTtl(java.lang.String workloadCertTtl) {
            this.workloadCertTtl = workloadCertTtl;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecMtls}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecMtls build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecMtls}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecMtls {
        private final java.lang.String controlPlaneTrustDomain;
        private final java.lang.Boolean enabled;
        private final java.lang.String sentryAddress;
        private final java.lang.String allowedClockSkew;
        private final java.util.List<imports.io.dapr.ConfigurationSpecMtlsTokenValidators> tokenValidators;
        private final java.lang.String workloadCertTtl;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.controlPlaneTrustDomain = software.amazon.jsii.Kernel.get(this, "controlPlaneTrustDomain", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.enabled = software.amazon.jsii.Kernel.get(this, "enabled", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.sentryAddress = software.amazon.jsii.Kernel.get(this, "sentryAddress", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.allowedClockSkew = software.amazon.jsii.Kernel.get(this, "allowedClockSkew", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.tokenValidators = software.amazon.jsii.Kernel.get(this, "tokenValidators", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecMtlsTokenValidators.class)));
            this.workloadCertTtl = software.amazon.jsii.Kernel.get(this, "workloadCertTtl", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.controlPlaneTrustDomain = java.util.Objects.requireNonNull(builder.controlPlaneTrustDomain, "controlPlaneTrustDomain is required");
            this.enabled = java.util.Objects.requireNonNull(builder.enabled, "enabled is required");
            this.sentryAddress = java.util.Objects.requireNonNull(builder.sentryAddress, "sentryAddress is required");
            this.allowedClockSkew = builder.allowedClockSkew;
            this.tokenValidators = (java.util.List<imports.io.dapr.ConfigurationSpecMtlsTokenValidators>)builder.tokenValidators;
            this.workloadCertTtl = builder.workloadCertTtl;
        }

        @Override
        public final java.lang.String getControlPlaneTrustDomain() {
            return this.controlPlaneTrustDomain;
        }

        @Override
        public final java.lang.Boolean getEnabled() {
            return this.enabled;
        }

        @Override
        public final java.lang.String getSentryAddress() {
            return this.sentryAddress;
        }

        @Override
        public final java.lang.String getAllowedClockSkew() {
            return this.allowedClockSkew;
        }

        @Override
        public final java.util.List<imports.io.dapr.ConfigurationSpecMtlsTokenValidators> getTokenValidators() {
            return this.tokenValidators;
        }

        @Override
        public final java.lang.String getWorkloadCertTtl() {
            return this.workloadCertTtl;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("controlPlaneTrustDomain", om.valueToTree(this.getControlPlaneTrustDomain()));
            data.set("enabled", om.valueToTree(this.getEnabled()));
            data.set("sentryAddress", om.valueToTree(this.getSentryAddress()));
            if (this.getAllowedClockSkew() != null) {
                data.set("allowedClockSkew", om.valueToTree(this.getAllowedClockSkew()));
            }
            if (this.getTokenValidators() != null) {
                data.set("tokenValidators", om.valueToTree(this.getTokenValidators()));
            }
            if (this.getWorkloadCertTtl() != null) {
                data.set("workloadCertTtl", om.valueToTree(this.getWorkloadCertTtl()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecMtls"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecMtls.Jsii$Proxy that = (ConfigurationSpecMtls.Jsii$Proxy) o;

            if (!controlPlaneTrustDomain.equals(that.controlPlaneTrustDomain)) return false;
            if (!enabled.equals(that.enabled)) return false;
            if (!sentryAddress.equals(that.sentryAddress)) return false;
            if (this.allowedClockSkew != null ? !this.allowedClockSkew.equals(that.allowedClockSkew) : that.allowedClockSkew != null) return false;
            if (this.tokenValidators != null ? !this.tokenValidators.equals(that.tokenValidators) : that.tokenValidators != null) return false;
            return this.workloadCertTtl != null ? this.workloadCertTtl.equals(that.workloadCertTtl) : that.workloadCertTtl == null;
        }

        @Override
        public final int hashCode() {
            int result = this.controlPlaneTrustDomain.hashCode();
            result = 31 * result + (this.enabled.hashCode());
            result = 31 * result + (this.sentryAddress.hashCode());
            result = 31 * result + (this.allowedClockSkew != null ? this.allowedClockSkew.hashCode() : 0);
            result = 31 * result + (this.tokenValidators != null ? this.tokenValidators.hashCode() : 0);
            result = 31 * result + (this.workloadCertTtl != null ? this.workloadCertTtl.hashCode() : 0);
            return result;
        }
    }
}
