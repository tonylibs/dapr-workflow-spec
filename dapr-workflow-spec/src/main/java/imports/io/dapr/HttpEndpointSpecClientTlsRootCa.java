package imports.io.dapr;

/**
 * TLSDocument describes and in-line or pointer to a document to build a TLS configuration.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.453Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.HttpEndpointSpecClientTlsRootCa")
@software.amazon.jsii.Jsii.Proxy(HttpEndpointSpecClientTlsRootCa.Jsii$Proxy.class)
public interface HttpEndpointSpecClientTlsRootCa extends software.amazon.jsii.JsiiSerializable {

    /**
     * SecretKeyRef is the reference of a value in a secret store component.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.HttpEndpointSpecClientTlsRootCaSecretKeyRef getSecretKeyRef() {
        return null;
    }

    /**
     * Value of the property, in plaintext.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Object getValue() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link HttpEndpointSpecClientTlsRootCa}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link HttpEndpointSpecClientTlsRootCa}
     */
    public static final class Builder implements software.amazon.jsii.Builder<HttpEndpointSpecClientTlsRootCa> {
        imports.io.dapr.HttpEndpointSpecClientTlsRootCaSecretKeyRef secretKeyRef;
        java.lang.Object value;

        /**
         * Sets the value of {@link HttpEndpointSpecClientTlsRootCa#getSecretKeyRef}
         * @param secretKeyRef SecretKeyRef is the reference of a value in a secret store component.
         * @return {@code this}
         */
        public Builder secretKeyRef(imports.io.dapr.HttpEndpointSpecClientTlsRootCaSecretKeyRef secretKeyRef) {
            this.secretKeyRef = secretKeyRef;
            return this;
        }

        /**
         * Sets the value of {@link HttpEndpointSpecClientTlsRootCa#getValue}
         * @param value Value of the property, in plaintext.
         * @return {@code this}
         */
        public Builder value(java.lang.Object value) {
            this.value = value;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link HttpEndpointSpecClientTlsRootCa}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public HttpEndpointSpecClientTlsRootCa build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link HttpEndpointSpecClientTlsRootCa}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements HttpEndpointSpecClientTlsRootCa {
        private final imports.io.dapr.HttpEndpointSpecClientTlsRootCaSecretKeyRef secretKeyRef;
        private final java.lang.Object value;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.secretKeyRef = software.amazon.jsii.Kernel.get(this, "secretKeyRef", software.amazon.jsii.NativeType.forClass(imports.io.dapr.HttpEndpointSpecClientTlsRootCaSecretKeyRef.class));
            this.value = software.amazon.jsii.Kernel.get(this, "value", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.secretKeyRef = builder.secretKeyRef;
            this.value = builder.value;
        }

        @Override
        public final imports.io.dapr.HttpEndpointSpecClientTlsRootCaSecretKeyRef getSecretKeyRef() {
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

            if (this.getSecretKeyRef() != null) {
                data.set("secretKeyRef", om.valueToTree(this.getSecretKeyRef()));
            }
            if (this.getValue() != null) {
                data.set("value", om.valueToTree(this.getValue()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.HttpEndpointSpecClientTlsRootCa"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            HttpEndpointSpecClientTlsRootCa.Jsii$Proxy that = (HttpEndpointSpecClientTlsRootCa.Jsii$Proxy) o;

            if (this.secretKeyRef != null ? !this.secretKeyRef.equals(that.secretKeyRef) : that.secretKeyRef != null) return false;
            return this.value != null ? this.value.equals(that.value) : that.value == null;
        }

        @Override
        public final int hashCode() {
            int result = this.secretKeyRef != null ? this.secretKeyRef.hashCode() : 0;
            result = 31 * result + (this.value != null ? this.value.hashCode() : 0);
            return result;
        }
    }
}
