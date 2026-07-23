package imports.dev.knative.serving;

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
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.146Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.DomainMappingSpecRef")
@software.amazon.jsii.Jsii.Proxy(DomainMappingSpecRef.Jsii$Proxy.class)
public interface DomainMappingSpecRef extends software.amazon.jsii.JsiiSerializable {

    /**
     * Kind of the referent.
     * <p>
     * More info: https://git.k8s.io/community/contributors/devel/sig-architecture/api-conventions.md#types-kinds
     */
    @org.jetbrains.annotations.NotNull java.lang.String getKind();

    /**
     * Name of the referent.
     * <p>
     * More info: https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#names
     */
    @org.jetbrains.annotations.NotNull java.lang.String getName();

    /**
     * Address points to a specific Address Name.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getAddress() {
        return null;
    }

    /**
     * API version of the referent.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getApiVersion() {
        return null;
    }

    /**
     * Group of the API, without the version of the group.
     * <p>
     * This can be used as an alternative to the APIVersion, and then resolved using ResolveGroup.
     * Note: This API is EXPERIMENTAL and might break anytime. For more details: https://github.com/knative/eventing/issues/5086
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getGroup() {
        return null;
    }

    /**
     * Namespace of the referent.
     * <p>
     * More info: https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/
     * This is optional field, it gets defaulted to the object holding it if left out.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getNamespace() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link DomainMappingSpecRef}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link DomainMappingSpecRef}
     */
    public static final class Builder implements software.amazon.jsii.Builder<DomainMappingSpecRef> {
        java.lang.String kind;
        java.lang.String name;
        java.lang.String address;
        java.lang.String apiVersion;
        java.lang.String group;
        java.lang.String namespace;

        /**
         * Sets the value of {@link DomainMappingSpecRef#getKind}
         * @param kind Kind of the referent. This parameter is required.
         *             More info: https://git.k8s.io/community/contributors/devel/sig-architecture/api-conventions.md#types-kinds
         * @return {@code this}
         */
        public Builder kind(java.lang.String kind) {
            this.kind = kind;
            return this;
        }

        /**
         * Sets the value of {@link DomainMappingSpecRef#getName}
         * @param name Name of the referent. This parameter is required.
         *             More info: https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#names
         * @return {@code this}
         */
        public Builder name(java.lang.String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the value of {@link DomainMappingSpecRef#getAddress}
         * @param address Address points to a specific Address Name.
         * @return {@code this}
         */
        public Builder address(java.lang.String address) {
            this.address = address;
            return this;
        }

        /**
         * Sets the value of {@link DomainMappingSpecRef#getApiVersion}
         * @param apiVersion API version of the referent.
         * @return {@code this}
         */
        public Builder apiVersion(java.lang.String apiVersion) {
            this.apiVersion = apiVersion;
            return this;
        }

        /**
         * Sets the value of {@link DomainMappingSpecRef#getGroup}
         * @param group Group of the API, without the version of the group.
         *              This can be used as an alternative to the APIVersion, and then resolved using ResolveGroup.
         *              Note: This API is EXPERIMENTAL and might break anytime. For more details: https://github.com/knative/eventing/issues/5086
         * @return {@code this}
         */
        public Builder group(java.lang.String group) {
            this.group = group;
            return this;
        }

        /**
         * Sets the value of {@link DomainMappingSpecRef#getNamespace}
         * @param namespace Namespace of the referent.
         *                  More info: https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/
         *                  This is optional field, it gets defaulted to the object holding it if left out.
         * @return {@code this}
         */
        public Builder namespace(java.lang.String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link DomainMappingSpecRef}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public DomainMappingSpecRef build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link DomainMappingSpecRef}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements DomainMappingSpecRef {
        private final java.lang.String kind;
        private final java.lang.String name;
        private final java.lang.String address;
        private final java.lang.String apiVersion;
        private final java.lang.String group;
        private final java.lang.String namespace;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.kind = software.amazon.jsii.Kernel.get(this, "kind", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.address = software.amazon.jsii.Kernel.get(this, "address", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.apiVersion = software.amazon.jsii.Kernel.get(this, "apiVersion", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.group = software.amazon.jsii.Kernel.get(this, "group", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.namespace = software.amazon.jsii.Kernel.get(this, "namespace", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.kind = java.util.Objects.requireNonNull(builder.kind, "kind is required");
            this.name = java.util.Objects.requireNonNull(builder.name, "name is required");
            this.address = builder.address;
            this.apiVersion = builder.apiVersion;
            this.group = builder.group;
            this.namespace = builder.namespace;
        }

        @Override
        public final java.lang.String getKind() {
            return this.kind;
        }

        @Override
        public final java.lang.String getName() {
            return this.name;
        }

        @Override
        public final java.lang.String getAddress() {
            return this.address;
        }

        @Override
        public final java.lang.String getApiVersion() {
            return this.apiVersion;
        }

        @Override
        public final java.lang.String getGroup() {
            return this.group;
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

            data.set("kind", om.valueToTree(this.getKind()));
            data.set("name", om.valueToTree(this.getName()));
            if (this.getAddress() != null) {
                data.set("address", om.valueToTree(this.getAddress()));
            }
            if (this.getApiVersion() != null) {
                data.set("apiVersion", om.valueToTree(this.getApiVersion()));
            }
            if (this.getGroup() != null) {
                data.set("group", om.valueToTree(this.getGroup()));
            }
            if (this.getNamespace() != null) {
                data.set("namespace", om.valueToTree(this.getNamespace()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.DomainMappingSpecRef"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DomainMappingSpecRef.Jsii$Proxy that = (DomainMappingSpecRef.Jsii$Proxy) o;

            if (!kind.equals(that.kind)) return false;
            if (!name.equals(that.name)) return false;
            if (this.address != null ? !this.address.equals(that.address) : that.address != null) return false;
            if (this.apiVersion != null ? !this.apiVersion.equals(that.apiVersion) : that.apiVersion != null) return false;
            if (this.group != null ? !this.group.equals(that.group) : that.group != null) return false;
            return this.namespace != null ? this.namespace.equals(that.namespace) : that.namespace == null;
        }

        @Override
        public final int hashCode() {
            int result = this.kind.hashCode();
            result = 31 * result + (this.name.hashCode());
            result = 31 * result + (this.address != null ? this.address.hashCode() : 0);
            result = 31 * result + (this.apiVersion != null ? this.apiVersion.hashCode() : 0);
            result = 31 * result + (this.group != null ? this.group.hashCode() : 0);
            result = 31 * result + (this.namespace != null ? this.namespace.hashCode() : 0);
            return result;
        }
    }
}
