package imports.dev.knative.internal.networking;

/**
 * IngressTLS describes the transport layer security associated with an Ingress.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:14.130Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.networking.$Module.class, fqn = "devknativeinternalnetworking.IngressSpecTls")
@software.amazon.jsii.Jsii.Proxy(IngressSpecTls.Jsii$Proxy.class)
public interface IngressSpecTls extends software.amazon.jsii.JsiiSerializable {

    /**
     * Hosts is a list of hosts included in the TLS certificate.
     * <p>
     * The values in
     * this list must match the name/s used in the tlsSecret. Defaults to the
     * wildcard host setting for the loadbalancer controller fulfilling this
     * Ingress, if left unspecified.
     * <p>
     * Default: the
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getHosts() {
        return null;
    }

    /**
     * SecretName is the name of the secret used to terminate SSL traffic.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getSecretName() {
        return null;
    }

    /**
     * SecretNamespace is the namespace of the secret used to terminate SSL traffic.
     * <p>
     * If not set the namespace should be assumed to be the same as the Ingress.
     * If set the secret should have the same namespace as the Ingress otherwise
     * the behaviour is undefined and not supported.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getSecretNamespace() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link IngressSpecTls}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link IngressSpecTls}
     */
    public static final class Builder implements software.amazon.jsii.Builder<IngressSpecTls> {
        java.util.List<java.lang.String> hosts;
        java.lang.String secretName;
        java.lang.String secretNamespace;

        /**
         * Sets the value of {@link IngressSpecTls#getHosts}
         * @param hosts Hosts is a list of hosts included in the TLS certificate.
         *              The values in
         *              this list must match the name/s used in the tlsSecret. Defaults to the
         *              wildcard host setting for the loadbalancer controller fulfilling this
         *              Ingress, if left unspecified.
         * @return {@code this}
         */
        public Builder hosts(java.util.List<java.lang.String> hosts) {
            this.hosts = hosts;
            return this;
        }

        /**
         * Sets the value of {@link IngressSpecTls#getSecretName}
         * @param secretName SecretName is the name of the secret used to terminate SSL traffic.
         * @return {@code this}
         */
        public Builder secretName(java.lang.String secretName) {
            this.secretName = secretName;
            return this;
        }

        /**
         * Sets the value of {@link IngressSpecTls#getSecretNamespace}
         * @param secretNamespace SecretNamespace is the namespace of the secret used to terminate SSL traffic.
         *                        If not set the namespace should be assumed to be the same as the Ingress.
         *                        If set the secret should have the same namespace as the Ingress otherwise
         *                        the behaviour is undefined and not supported.
         * @return {@code this}
         */
        public Builder secretNamespace(java.lang.String secretNamespace) {
            this.secretNamespace = secretNamespace;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link IngressSpecTls}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public IngressSpecTls build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link IngressSpecTls}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements IngressSpecTls {
        private final java.util.List<java.lang.String> hosts;
        private final java.lang.String secretName;
        private final java.lang.String secretNamespace;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.hosts = software.amazon.jsii.Kernel.get(this, "hosts", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.secretName = software.amazon.jsii.Kernel.get(this, "secretName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.secretNamespace = software.amazon.jsii.Kernel.get(this, "secretNamespace", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.hosts = builder.hosts;
            this.secretName = builder.secretName;
            this.secretNamespace = builder.secretNamespace;
        }

        @Override
        public final java.util.List<java.lang.String> getHosts() {
            return this.hosts;
        }

        @Override
        public final java.lang.String getSecretName() {
            return this.secretName;
        }

        @Override
        public final java.lang.String getSecretNamespace() {
            return this.secretNamespace;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getHosts() != null) {
                data.set("hosts", om.valueToTree(this.getHosts()));
            }
            if (this.getSecretName() != null) {
                data.set("secretName", om.valueToTree(this.getSecretName()));
            }
            if (this.getSecretNamespace() != null) {
                data.set("secretNamespace", om.valueToTree(this.getSecretNamespace()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeinternalnetworking.IngressSpecTls"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            IngressSpecTls.Jsii$Proxy that = (IngressSpecTls.Jsii$Proxy) o;

            if (this.hosts != null ? !this.hosts.equals(that.hosts) : that.hosts != null) return false;
            if (this.secretName != null ? !this.secretName.equals(that.secretName) : that.secretName != null) return false;
            return this.secretNamespace != null ? this.secretNamespace.equals(that.secretNamespace) : that.secretNamespace == null;
        }

        @Override
        public final int hashCode() {
            int result = this.hosts != null ? this.hosts.hashCode() : 0;
            result = 31 * result + (this.secretName != null ? this.secretName.hashCode() : 0);
            result = 31 * result + (this.secretNamespace != null ? this.secretNamespace.hashCode() : 0);
            return result;
        }
    }
}
