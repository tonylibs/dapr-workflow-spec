package imports.io.dapr;

/**
 * TLS describes how to build client or server TLS configurations.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.452Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.HttpEndpointSpecClientTls")
@software.amazon.jsii.Jsii.Proxy(HttpEndpointSpecClientTls.Jsii$Proxy.class)
public interface HttpEndpointSpecClientTls extends software.amazon.jsii.JsiiSerializable {

    /**
     * TLSDocument describes and in-line or pointer to a document to build a TLS configuration.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.HttpEndpointSpecClientTlsCertificate getCertificate() {
        return null;
    }

    /**
     * TLSDocument describes and in-line or pointer to a document to build a TLS configuration.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.HttpEndpointSpecClientTlsPrivateKey getPrivateKey() {
        return null;
    }

    /**
     * Renegotiation sets the underlying tls negotiation strategy for an http channel.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.HttpEndpointSpecClientTlsRenegotiation getRenegotiation() {
        return null;
    }

    /**
     * TLSDocument describes and in-line or pointer to a document to build a TLS configuration.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.HttpEndpointSpecClientTlsRootCa getRootCa() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link HttpEndpointSpecClientTls}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link HttpEndpointSpecClientTls}
     */
    public static final class Builder implements software.amazon.jsii.Builder<HttpEndpointSpecClientTls> {
        imports.io.dapr.HttpEndpointSpecClientTlsCertificate certificate;
        imports.io.dapr.HttpEndpointSpecClientTlsPrivateKey privateKey;
        imports.io.dapr.HttpEndpointSpecClientTlsRenegotiation renegotiation;
        imports.io.dapr.HttpEndpointSpecClientTlsRootCa rootCa;

        /**
         * Sets the value of {@link HttpEndpointSpecClientTls#getCertificate}
         * @param certificate TLSDocument describes and in-line or pointer to a document to build a TLS configuration.
         * @return {@code this}
         */
        public Builder certificate(imports.io.dapr.HttpEndpointSpecClientTlsCertificate certificate) {
            this.certificate = certificate;
            return this;
        }

        /**
         * Sets the value of {@link HttpEndpointSpecClientTls#getPrivateKey}
         * @param privateKey TLSDocument describes and in-line or pointer to a document to build a TLS configuration.
         * @return {@code this}
         */
        public Builder privateKey(imports.io.dapr.HttpEndpointSpecClientTlsPrivateKey privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        /**
         * Sets the value of {@link HttpEndpointSpecClientTls#getRenegotiation}
         * @param renegotiation Renegotiation sets the underlying tls negotiation strategy for an http channel.
         * @return {@code this}
         */
        public Builder renegotiation(imports.io.dapr.HttpEndpointSpecClientTlsRenegotiation renegotiation) {
            this.renegotiation = renegotiation;
            return this;
        }

        /**
         * Sets the value of {@link HttpEndpointSpecClientTls#getRootCa}
         * @param rootCa TLSDocument describes and in-line or pointer to a document to build a TLS configuration.
         * @return {@code this}
         */
        public Builder rootCa(imports.io.dapr.HttpEndpointSpecClientTlsRootCa rootCa) {
            this.rootCa = rootCa;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link HttpEndpointSpecClientTls}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public HttpEndpointSpecClientTls build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link HttpEndpointSpecClientTls}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements HttpEndpointSpecClientTls {
        private final imports.io.dapr.HttpEndpointSpecClientTlsCertificate certificate;
        private final imports.io.dapr.HttpEndpointSpecClientTlsPrivateKey privateKey;
        private final imports.io.dapr.HttpEndpointSpecClientTlsRenegotiation renegotiation;
        private final imports.io.dapr.HttpEndpointSpecClientTlsRootCa rootCa;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.certificate = software.amazon.jsii.Kernel.get(this, "certificate", software.amazon.jsii.NativeType.forClass(imports.io.dapr.HttpEndpointSpecClientTlsCertificate.class));
            this.privateKey = software.amazon.jsii.Kernel.get(this, "privateKey", software.amazon.jsii.NativeType.forClass(imports.io.dapr.HttpEndpointSpecClientTlsPrivateKey.class));
            this.renegotiation = software.amazon.jsii.Kernel.get(this, "renegotiation", software.amazon.jsii.NativeType.forClass(imports.io.dapr.HttpEndpointSpecClientTlsRenegotiation.class));
            this.rootCa = software.amazon.jsii.Kernel.get(this, "rootCa", software.amazon.jsii.NativeType.forClass(imports.io.dapr.HttpEndpointSpecClientTlsRootCa.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.certificate = builder.certificate;
            this.privateKey = builder.privateKey;
            this.renegotiation = builder.renegotiation;
            this.rootCa = builder.rootCa;
        }

        @Override
        public final imports.io.dapr.HttpEndpointSpecClientTlsCertificate getCertificate() {
            return this.certificate;
        }

        @Override
        public final imports.io.dapr.HttpEndpointSpecClientTlsPrivateKey getPrivateKey() {
            return this.privateKey;
        }

        @Override
        public final imports.io.dapr.HttpEndpointSpecClientTlsRenegotiation getRenegotiation() {
            return this.renegotiation;
        }

        @Override
        public final imports.io.dapr.HttpEndpointSpecClientTlsRootCa getRootCa() {
            return this.rootCa;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getCertificate() != null) {
                data.set("certificate", om.valueToTree(this.getCertificate()));
            }
            if (this.getPrivateKey() != null) {
                data.set("privateKey", om.valueToTree(this.getPrivateKey()));
            }
            if (this.getRenegotiation() != null) {
                data.set("renegotiation", om.valueToTree(this.getRenegotiation()));
            }
            if (this.getRootCa() != null) {
                data.set("rootCa", om.valueToTree(this.getRootCa()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.HttpEndpointSpecClientTls"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            HttpEndpointSpecClientTls.Jsii$Proxy that = (HttpEndpointSpecClientTls.Jsii$Proxy) o;

            if (this.certificate != null ? !this.certificate.equals(that.certificate) : that.certificate != null) return false;
            if (this.privateKey != null ? !this.privateKey.equals(that.privateKey) : that.privateKey != null) return false;
            if (this.renegotiation != null ? !this.renegotiation.equals(that.renegotiation) : that.renegotiation != null) return false;
            return this.rootCa != null ? this.rootCa.equals(that.rootCa) : that.rootCa == null;
        }

        @Override
        public final int hashCode() {
            int result = this.certificate != null ? this.certificate.hashCode() : 0;
            result = 31 * result + (this.privateKey != null ? this.privateKey.hashCode() : 0);
            result = 31 * result + (this.renegotiation != null ? this.renegotiation.hashCode() : 0);
            result = 31 * result + (this.rootCa != null ? this.rootCa.hashCode() : 0);
            return result;
        }
    }
}
