package imports.io.dapr;

/**
 * Auth represents authentication details for the component.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.433Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ComponentAuth")
@software.amazon.jsii.Jsii.Proxy(ComponentAuth.Jsii$Proxy.class)
public interface ComponentAuth extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getSecretStore();

    /**
     * @return a {@link Builder} of {@link ComponentAuth}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ComponentAuth}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ComponentAuth> {
        java.lang.String secretStore;

        /**
         * Sets the value of {@link ComponentAuth#getSecretStore}
         * @param secretStore the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder secretStore(java.lang.String secretStore) {
            this.secretStore = secretStore;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ComponentAuth}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ComponentAuth build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ComponentAuth}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ComponentAuth {
        private final java.lang.String secretStore;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.secretStore = software.amazon.jsii.Kernel.get(this, "secretStore", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.secretStore = java.util.Objects.requireNonNull(builder.secretStore, "secretStore is required");
        }

        @Override
        public final java.lang.String getSecretStore() {
            return this.secretStore;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("secretStore", om.valueToTree(this.getSecretStore()));

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ComponentAuth"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ComponentAuth.Jsii$Proxy that = (ComponentAuth.Jsii$Proxy) o;

            return this.secretStore.equals(that.secretStore);
        }

        @Override
        public final int hashCode() {
            int result = this.secretStore.hashCode();
            return result;
        }
    }
}
