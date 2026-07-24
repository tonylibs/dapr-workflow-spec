package imports.dev.knative.internal.networking;

/**
 * Spec is the desired state of the ClusterDomainClaim.
 * <p>
 * More info: https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#spec-and-status
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:14.117Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.networking.$Module.class, fqn = "devknativeinternalnetworking.ClusterDomainClaimSpec")
@software.amazon.jsii.Jsii.Proxy(ClusterDomainClaimSpec.Jsii$Proxy.class)
public interface ClusterDomainClaimSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     * Namespace is the namespace which is allowed to create a DomainMapping using this ClusterDomainClaim's name.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getNamespace();

    /**
     * @return a {@link Builder} of {@link ClusterDomainClaimSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ClusterDomainClaimSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ClusterDomainClaimSpec> {
        java.lang.String namespace;

        /**
         * Sets the value of {@link ClusterDomainClaimSpec#getNamespace}
         * @param namespace Namespace is the namespace which is allowed to create a DomainMapping using this ClusterDomainClaim's name. This parameter is required.
         * @return {@code this}
         */
        public Builder namespace(java.lang.String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ClusterDomainClaimSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ClusterDomainClaimSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ClusterDomainClaimSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ClusterDomainClaimSpec {
        private final java.lang.String namespace;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.namespace = software.amazon.jsii.Kernel.get(this, "namespace", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.namespace = java.util.Objects.requireNonNull(builder.namespace, "namespace is required");
        }

        @Override
        public final java.lang.String getNamespace() {
            return this.namespace;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("namespace", om.valueToTree(this.getNamespace()));

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeinternalnetworking.ClusterDomainClaimSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ClusterDomainClaimSpec.Jsii$Proxy that = (ClusterDomainClaimSpec.Jsii$Proxy) o;

            return this.namespace.equals(that.namespace);
        }

        @Override
        public final int hashCode() {
            int result = this.namespace.hashCode();
            return result;
        }
    }
}
