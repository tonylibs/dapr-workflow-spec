package imports.dev.knative.serving;

/**
 * Spec is the desired state of the DomainMapping.
 * <p>
 * More info: https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#spec-and-status
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.145Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.DomainMappingSpec")
@software.amazon.jsii.Jsii.Proxy(DomainMappingSpec.Jsii$Proxy.class)
public interface DomainMappingSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     * Ref specifies the target of the Domain Mapping.
     * <p>
     * The object identified by the Ref must be an Addressable with a URL of the
     * form <code>{name}.{namespace}.{domain}</code> where <code>{domain}</code> is the cluster domain,
     * and <code>{name}</code> and <code>{namespace}</code> are the name and namespace of a Kubernetes
     * Service.
     * <p>
     * This contract is satisfied by Knative types such as Knative Services and
     * Knative Routes, and by Kubernetes Services.
     */
    @org.jetbrains.annotations.NotNull imports.dev.knative.serving.DomainMappingSpecRef getRef();

    /**
     * TLS allows the DomainMapping to terminate TLS traffic with an existing secret.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.DomainMappingSpecTls getTls() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link DomainMappingSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link DomainMappingSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<DomainMappingSpec> {
        imports.dev.knative.serving.DomainMappingSpecRef ref;
        imports.dev.knative.serving.DomainMappingSpecTls tls;

        /**
         * Sets the value of {@link DomainMappingSpec#getRef}
         * @param ref Ref specifies the target of the Domain Mapping. This parameter is required.
         *            The object identified by the Ref must be an Addressable with a URL of the
         *            form <code>{name}.{namespace}.{domain}</code> where <code>{domain}</code> is the cluster domain,
         *            and <code>{name}</code> and <code>{namespace}</code> are the name and namespace of a Kubernetes
         *            Service.
         *            <p>
         *            This contract is satisfied by Knative types such as Knative Services and
         *            Knative Routes, and by Kubernetes Services.
         * @return {@code this}
         */
        public Builder ref(imports.dev.knative.serving.DomainMappingSpecRef ref) {
            this.ref = ref;
            return this;
        }

        /**
         * Sets the value of {@link DomainMappingSpec#getTls}
         * @param tls TLS allows the DomainMapping to terminate TLS traffic with an existing secret.
         * @return {@code this}
         */
        public Builder tls(imports.dev.knative.serving.DomainMappingSpecTls tls) {
            this.tls = tls;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link DomainMappingSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public DomainMappingSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link DomainMappingSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements DomainMappingSpec {
        private final imports.dev.knative.serving.DomainMappingSpecRef ref;
        private final imports.dev.knative.serving.DomainMappingSpecTls tls;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.ref = software.amazon.jsii.Kernel.get(this, "ref", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.DomainMappingSpecRef.class));
            this.tls = software.amazon.jsii.Kernel.get(this, "tls", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.DomainMappingSpecTls.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.ref = java.util.Objects.requireNonNull(builder.ref, "ref is required");
            this.tls = builder.tls;
        }

        @Override
        public final imports.dev.knative.serving.DomainMappingSpecRef getRef() {
            return this.ref;
        }

        @Override
        public final imports.dev.knative.serving.DomainMappingSpecTls getTls() {
            return this.tls;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("ref", om.valueToTree(this.getRef()));
            if (this.getTls() != null) {
                data.set("tls", om.valueToTree(this.getTls()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.DomainMappingSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DomainMappingSpec.Jsii$Proxy that = (DomainMappingSpec.Jsii$Proxy) o;

            if (!ref.equals(that.ref)) return false;
            return this.tls != null ? this.tls.equals(that.tls) : that.tls == null;
        }

        @Override
        public final int hashCode() {
            int result = this.ref.hashCode();
            result = 31 * result + (this.tls != null ? this.tls.hashCode() : 0);
            return result;
        }
    }
}
