package imports.dev.knative.internal.networking;

/**
 * Spec is the desired state of the Ingress.
 * <p>
 * More info: https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#spec-and-status
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:14.119Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.networking.$Module.class, fqn = "devknativeinternalnetworking.IngressSpec")
@software.amazon.jsii.Jsii.Proxy(IngressSpec.Jsii$Proxy.class)
public interface IngressSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     * HTTPOption is the option of HTTP.
     * <p>
     * It has the following two values:
     * <code>HTTPOptionEnabled</code>, <code>HTTPOptionRedirected</code>
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getHttpOption() {
        return null;
    }

    /**
     * A list of host rules used to configure the Ingress.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.internal.networking.IngressSpecRules> getRules() {
        return null;
    }

    /**
     * TLS configuration.
     * <p>
     * Currently Ingress only supports a single TLS
     * port: 443. If multiple members of this list specify different hosts, they
     * will be multiplexed on the same port according to the hostname specified
     * through the SNI TLS extension, if the ingress controller fulfilling the
     * ingress supports SNI.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.internal.networking.IngressSpecTls> getTls() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link IngressSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link IngressSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<IngressSpec> {
        java.lang.String httpOption;
        java.util.List<imports.dev.knative.internal.networking.IngressSpecRules> rules;
        java.util.List<imports.dev.knative.internal.networking.IngressSpecTls> tls;

        /**
         * Sets the value of {@link IngressSpec#getHttpOption}
         * @param httpOption HTTPOption is the option of HTTP.
         *                   It has the following two values:
         *                   <code>HTTPOptionEnabled</code>, <code>HTTPOptionRedirected</code>
         * @return {@code this}
         */
        public Builder httpOption(java.lang.String httpOption) {
            this.httpOption = httpOption;
            return this;
        }

        /**
         * Sets the value of {@link IngressSpec#getRules}
         * @param rules A list of host rules used to configure the Ingress.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder rules(java.util.List<? extends imports.dev.knative.internal.networking.IngressSpecRules> rules) {
            this.rules = (java.util.List<imports.dev.knative.internal.networking.IngressSpecRules>)rules;
            return this;
        }

        /**
         * Sets the value of {@link IngressSpec#getTls}
         * @param tls TLS configuration.
         *            Currently Ingress only supports a single TLS
         *            port: 443. If multiple members of this list specify different hosts, they
         *            will be multiplexed on the same port according to the hostname specified
         *            through the SNI TLS extension, if the ingress controller fulfilling the
         *            ingress supports SNI.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder tls(java.util.List<? extends imports.dev.knative.internal.networking.IngressSpecTls> tls) {
            this.tls = (java.util.List<imports.dev.knative.internal.networking.IngressSpecTls>)tls;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link IngressSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public IngressSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link IngressSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements IngressSpec {
        private final java.lang.String httpOption;
        private final java.util.List<imports.dev.knative.internal.networking.IngressSpecRules> rules;
        private final java.util.List<imports.dev.knative.internal.networking.IngressSpecTls> tls;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.httpOption = software.amazon.jsii.Kernel.get(this, "httpOption", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.rules = software.amazon.jsii.Kernel.get(this, "rules", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.internal.networking.IngressSpecRules.class)));
            this.tls = software.amazon.jsii.Kernel.get(this, "tls", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.internal.networking.IngressSpecTls.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.httpOption = builder.httpOption;
            this.rules = (java.util.List<imports.dev.knative.internal.networking.IngressSpecRules>)builder.rules;
            this.tls = (java.util.List<imports.dev.knative.internal.networking.IngressSpecTls>)builder.tls;
        }

        @Override
        public final java.lang.String getHttpOption() {
            return this.httpOption;
        }

        @Override
        public final java.util.List<imports.dev.knative.internal.networking.IngressSpecRules> getRules() {
            return this.rules;
        }

        @Override
        public final java.util.List<imports.dev.knative.internal.networking.IngressSpecTls> getTls() {
            return this.tls;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getHttpOption() != null) {
                data.set("httpOption", om.valueToTree(this.getHttpOption()));
            }
            if (this.getRules() != null) {
                data.set("rules", om.valueToTree(this.getRules()));
            }
            if (this.getTls() != null) {
                data.set("tls", om.valueToTree(this.getTls()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeinternalnetworking.IngressSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            IngressSpec.Jsii$Proxy that = (IngressSpec.Jsii$Proxy) o;

            if (this.httpOption != null ? !this.httpOption.equals(that.httpOption) : that.httpOption != null) return false;
            if (this.rules != null ? !this.rules.equals(that.rules) : that.rules != null) return false;
            return this.tls != null ? this.tls.equals(that.tls) : that.tls == null;
        }

        @Override
        public final int hashCode() {
            int result = this.httpOption != null ? this.httpOption.hashCode() : 0;
            result = 31 * result + (this.rules != null ? this.rules.hashCode() : 0);
            result = 31 * result + (this.tls != null ? this.tls.hashCode() : 0);
            return result;
        }
    }
}
