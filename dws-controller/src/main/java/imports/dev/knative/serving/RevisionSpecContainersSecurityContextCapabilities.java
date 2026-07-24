package imports.dev.knative.serving;

/**
 * The capabilities to add/drop when running containers.
 * <p>
 * Defaults to the default set of capabilities granted by the container runtime.
 * Note that this field cannot be set when spec.os.name is windows.
 * <p>
 * Default: the default set of capabilities granted by the container runtime.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.163Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.RevisionSpecContainersSecurityContextCapabilities")
@software.amazon.jsii.Jsii.Proxy(RevisionSpecContainersSecurityContextCapabilities.Jsii$Proxy.class)
public interface RevisionSpecContainersSecurityContextCapabilities extends software.amazon.jsii.JsiiSerializable {

    /**
     * This is accessible behind a feature flag - kubernetes.containerspec-addcapabilities.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getAdd() {
        return null;
    }

    /**
     * Removed capabilities.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getDrop() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link RevisionSpecContainersSecurityContextCapabilities}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link RevisionSpecContainersSecurityContextCapabilities}
     */
    public static final class Builder implements software.amazon.jsii.Builder<RevisionSpecContainersSecurityContextCapabilities> {
        java.util.List<java.lang.String> add;
        java.util.List<java.lang.String> drop;

        /**
         * Sets the value of {@link RevisionSpecContainersSecurityContextCapabilities#getAdd}
         * @param add This is accessible behind a feature flag - kubernetes.containerspec-addcapabilities.
         * @return {@code this}
         */
        public Builder add(java.util.List<java.lang.String> add) {
            this.add = add;
            return this;
        }

        /**
         * Sets the value of {@link RevisionSpecContainersSecurityContextCapabilities#getDrop}
         * @param drop Removed capabilities.
         * @return {@code this}
         */
        public Builder drop(java.util.List<java.lang.String> drop) {
            this.drop = drop;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link RevisionSpecContainersSecurityContextCapabilities}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public RevisionSpecContainersSecurityContextCapabilities build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link RevisionSpecContainersSecurityContextCapabilities}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements RevisionSpecContainersSecurityContextCapabilities {
        private final java.util.List<java.lang.String> add;
        private final java.util.List<java.lang.String> drop;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.add = software.amazon.jsii.Kernel.get(this, "add", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.drop = software.amazon.jsii.Kernel.get(this, "drop", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.add = builder.add;
            this.drop = builder.drop;
        }

        @Override
        public final java.util.List<java.lang.String> getAdd() {
            return this.add;
        }

        @Override
        public final java.util.List<java.lang.String> getDrop() {
            return this.drop;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getAdd() != null) {
                data.set("add", om.valueToTree(this.getAdd()));
            }
            if (this.getDrop() != null) {
                data.set("drop", om.valueToTree(this.getDrop()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.RevisionSpecContainersSecurityContextCapabilities"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RevisionSpecContainersSecurityContextCapabilities.Jsii$Proxy that = (RevisionSpecContainersSecurityContextCapabilities.Jsii$Proxy) o;

            if (this.add != null ? !this.add.equals(that.add) : that.add != null) return false;
            return this.drop != null ? this.drop.equals(that.drop) : that.drop == null;
        }

        @Override
        public final int hashCode() {
            int result = this.add != null ? this.add.hashCode() : 0;
            result = 31 * result + (this.drop != null ? this.drop.hashCode() : 0);
            return result;
        }
    }
}
