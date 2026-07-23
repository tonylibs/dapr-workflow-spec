package imports.dev.knative.serving;

/**
 * EnvFromSource represents the source of a set of ConfigMaps or Secrets.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.154Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.RevisionSpecContainersEnvFrom")
@software.amazon.jsii.Jsii.Proxy(RevisionSpecContainersEnvFrom.Jsii$Proxy.class)
public interface RevisionSpecContainersEnvFrom extends software.amazon.jsii.JsiiSerializable {

    /**
     * The ConfigMap to select from.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.RevisionSpecContainersEnvFromConfigMapRef getConfigMapRef() {
        return null;
    }

    /**
     * Optional text to prepend to the name of each environment variable.
     * <p>
     * May consist of any printable ASCII characters except '='.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getPrefix() {
        return null;
    }

    /**
     * The Secret to select from.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.RevisionSpecContainersEnvFromSecretRef getSecretRef() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link RevisionSpecContainersEnvFrom}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link RevisionSpecContainersEnvFrom}
     */
    public static final class Builder implements software.amazon.jsii.Builder<RevisionSpecContainersEnvFrom> {
        imports.dev.knative.serving.RevisionSpecContainersEnvFromConfigMapRef configMapRef;
        java.lang.String prefix;
        imports.dev.knative.serving.RevisionSpecContainersEnvFromSecretRef secretRef;

        /**
         * Sets the value of {@link RevisionSpecContainersEnvFrom#getConfigMapRef}
         * @param configMapRef The ConfigMap to select from.
         * @return {@code this}
         */
        public Builder configMapRef(imports.dev.knative.serving.RevisionSpecContainersEnvFromConfigMapRef configMapRef) {
            this.configMapRef = configMapRef;
            return this;
        }

        /**
         * Sets the value of {@link RevisionSpecContainersEnvFrom#getPrefix}
         * @param prefix Optional text to prepend to the name of each environment variable.
         *               May consist of any printable ASCII characters except '='.
         * @return {@code this}
         */
        public Builder prefix(java.lang.String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * Sets the value of {@link RevisionSpecContainersEnvFrom#getSecretRef}
         * @param secretRef The Secret to select from.
         * @return {@code this}
         */
        public Builder secretRef(imports.dev.knative.serving.RevisionSpecContainersEnvFromSecretRef secretRef) {
            this.secretRef = secretRef;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link RevisionSpecContainersEnvFrom}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public RevisionSpecContainersEnvFrom build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link RevisionSpecContainersEnvFrom}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements RevisionSpecContainersEnvFrom {
        private final imports.dev.knative.serving.RevisionSpecContainersEnvFromConfigMapRef configMapRef;
        private final java.lang.String prefix;
        private final imports.dev.knative.serving.RevisionSpecContainersEnvFromSecretRef secretRef;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.configMapRef = software.amazon.jsii.Kernel.get(this, "configMapRef", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.RevisionSpecContainersEnvFromConfigMapRef.class));
            this.prefix = software.amazon.jsii.Kernel.get(this, "prefix", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.secretRef = software.amazon.jsii.Kernel.get(this, "secretRef", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.RevisionSpecContainersEnvFromSecretRef.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.configMapRef = builder.configMapRef;
            this.prefix = builder.prefix;
            this.secretRef = builder.secretRef;
        }

        @Override
        public final imports.dev.knative.serving.RevisionSpecContainersEnvFromConfigMapRef getConfigMapRef() {
            return this.configMapRef;
        }

        @Override
        public final java.lang.String getPrefix() {
            return this.prefix;
        }

        @Override
        public final imports.dev.knative.serving.RevisionSpecContainersEnvFromSecretRef getSecretRef() {
            return this.secretRef;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getConfigMapRef() != null) {
                data.set("configMapRef", om.valueToTree(this.getConfigMapRef()));
            }
            if (this.getPrefix() != null) {
                data.set("prefix", om.valueToTree(this.getPrefix()));
            }
            if (this.getSecretRef() != null) {
                data.set("secretRef", om.valueToTree(this.getSecretRef()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.RevisionSpecContainersEnvFrom"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RevisionSpecContainersEnvFrom.Jsii$Proxy that = (RevisionSpecContainersEnvFrom.Jsii$Proxy) o;

            if (this.configMapRef != null ? !this.configMapRef.equals(that.configMapRef) : that.configMapRef != null) return false;
            if (this.prefix != null ? !this.prefix.equals(that.prefix) : that.prefix != null) return false;
            return this.secretRef != null ? this.secretRef.equals(that.secretRef) : that.secretRef == null;
        }

        @Override
        public final int hashCode() {
            int result = this.configMapRef != null ? this.configMapRef.hashCode() : 0;
            result = 31 * result + (this.prefix != null ? this.prefix.hashCode() : 0);
            result = 31 * result + (this.secretRef != null ? this.secretRef.hashCode() : 0);
            return result;
        }
    }
}
