package imports.dev.knative.internal.networking;

/**
 * Spec is the desired state of the Certificate.
 * <p>
 * More info: https://git.k8s.io/community/contributors/devel/sig-architecture/api-conventions.md#spec-and-status
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:14.114Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.networking.$Module.class, fqn = "devknativeinternalnetworking.CertificateSpec")
@software.amazon.jsii.Jsii.Proxy(CertificateSpec.Jsii$Proxy.class)
public interface CertificateSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     * DNSNames is a list of DNS names the Certificate could support.
     * <p>
     * The wildcard format of DNSNames (e.g. *.default.example.com) is supported.
     */
    @org.jetbrains.annotations.NotNull java.util.List<java.lang.String> getDnsNames();

    /**
     * SecretName is the name of the secret resource to store the SSL certificate in.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getSecretName();

    /**
     * Domain is the top level domain of the values for DNSNames.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getDomain() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link CertificateSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link CertificateSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<CertificateSpec> {
        java.util.List<java.lang.String> dnsNames;
        java.lang.String secretName;
        java.lang.String domain;

        /**
         * Sets the value of {@link CertificateSpec#getDnsNames}
         * @param dnsNames DNSNames is a list of DNS names the Certificate could support. This parameter is required.
         *                 The wildcard format of DNSNames (e.g. *.default.example.com) is supported.
         * @return {@code this}
         */
        public Builder dnsNames(java.util.List<java.lang.String> dnsNames) {
            this.dnsNames = dnsNames;
            return this;
        }

        /**
         * Sets the value of {@link CertificateSpec#getSecretName}
         * @param secretName SecretName is the name of the secret resource to store the SSL certificate in. This parameter is required.
         * @return {@code this}
         */
        public Builder secretName(java.lang.String secretName) {
            this.secretName = secretName;
            return this;
        }

        /**
         * Sets the value of {@link CertificateSpec#getDomain}
         * @param domain Domain is the top level domain of the values for DNSNames.
         * @return {@code this}
         */
        public Builder domain(java.lang.String domain) {
            this.domain = domain;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link CertificateSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public CertificateSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link CertificateSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements CertificateSpec {
        private final java.util.List<java.lang.String> dnsNames;
        private final java.lang.String secretName;
        private final java.lang.String domain;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.dnsNames = software.amazon.jsii.Kernel.get(this, "dnsNames", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.secretName = software.amazon.jsii.Kernel.get(this, "secretName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.domain = software.amazon.jsii.Kernel.get(this, "domain", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.dnsNames = java.util.Objects.requireNonNull(builder.dnsNames, "dnsNames is required");
            this.secretName = java.util.Objects.requireNonNull(builder.secretName, "secretName is required");
            this.domain = builder.domain;
        }

        @Override
        public final java.util.List<java.lang.String> getDnsNames() {
            return this.dnsNames;
        }

        @Override
        public final java.lang.String getSecretName() {
            return this.secretName;
        }

        @Override
        public final java.lang.String getDomain() {
            return this.domain;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("dnsNames", om.valueToTree(this.getDnsNames()));
            data.set("secretName", om.valueToTree(this.getSecretName()));
            if (this.getDomain() != null) {
                data.set("domain", om.valueToTree(this.getDomain()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeinternalnetworking.CertificateSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CertificateSpec.Jsii$Proxy that = (CertificateSpec.Jsii$Proxy) o;

            if (!dnsNames.equals(that.dnsNames)) return false;
            if (!secretName.equals(that.secretName)) return false;
            return this.domain != null ? this.domain.equals(that.domain) : that.domain == null;
        }

        @Override
        public final int hashCode() {
            int result = this.dnsNames.hashCode();
            result = 31 * result + (this.secretName.hashCode());
            result = 31 * result + (this.domain != null ? this.domain.hashCode() : 0);
            return result;
        }
    }
}
