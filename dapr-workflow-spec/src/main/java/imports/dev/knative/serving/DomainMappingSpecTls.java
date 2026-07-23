package imports.dev.knative.serving;

/**
 * TLS allows the DomainMapping to terminate TLS traffic with an existing secret.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.147Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.DomainMappingSpecTls")
@software.amazon.jsii.Jsii.Proxy(DomainMappingSpecTls.Jsii$Proxy.class)
public interface DomainMappingSpecTls extends software.amazon.jsii.JsiiSerializable {

    /**
     * SecretName is the name of the existing secret used to terminate TLS traffic.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getSecretName();

    /**
     * @return a {@link Builder} of {@link DomainMappingSpecTls}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link DomainMappingSpecTls}
     */
    public static final class Builder implements software.amazon.jsii.Builder<DomainMappingSpecTls> {
        java.lang.String secretName;

        /**
         * Sets the value of {@link DomainMappingSpecTls#getSecretName}
         * @param secretName SecretName is the name of the existing secret used to terminate TLS traffic. This parameter is required.
         * @return {@code this}
         */
        public Builder secretName(java.lang.String secretName) {
            this.secretName = secretName;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link DomainMappingSpecTls}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public DomainMappingSpecTls build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link DomainMappingSpecTls}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements DomainMappingSpecTls {
        private final java.lang.String secretName;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.secretName = software.amazon.jsii.Kernel.get(this, "secretName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.secretName = java.util.Objects.requireNonNull(builder.secretName, "secretName is required");
        }

        @Override
        public final java.lang.String getSecretName() {
            return this.secretName;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("secretName", om.valueToTree(this.getSecretName()));

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.DomainMappingSpecTls"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DomainMappingSpecTls.Jsii$Proxy that = (DomainMappingSpecTls.Jsii$Proxy) o;

            return this.secretName.equals(that.secretName);
        }

        @Override
        public final int hashCode() {
            int result = this.secretName.hashCode();
            return result;
        }
    }
}
