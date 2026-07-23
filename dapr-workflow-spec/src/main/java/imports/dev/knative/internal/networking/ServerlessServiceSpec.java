package imports.dev.knative.internal.networking;

/**
 * Spec is the desired state of the ServerlessService.
 * <p>
 * More info: https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#spec-and-status
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:14.132Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.networking.$Module.class, fqn = "devknativeinternalnetworking.ServerlessServiceSpec")
@software.amazon.jsii.Jsii.Proxy(ServerlessServiceSpec.Jsii$Proxy.class)
public interface ServerlessServiceSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     * ObjectRef defines the resource that this ServerlessService is responsible for making "serverless".
     */
    @org.jetbrains.annotations.NotNull imports.dev.knative.internal.networking.ServerlessServiceSpecObjectRef getObjectRef();

    /**
     * The application-layer protocol.
     * <p>
     * Matches <code>RevisionProtocolType</code> set on the owning pa/revision.
     * serving imports networking, so just use string.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getProtocolType();

    /**
     * Mode describes the mode of operation of the ServerlessService.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getMode() {
        return null;
    }

    /**
     * NumActivators contains number of Activators that this revision should be assigned.
     * <p>
     * O means — assign all.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getNumActivators() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ServerlessServiceSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ServerlessServiceSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ServerlessServiceSpec> {
        imports.dev.knative.internal.networking.ServerlessServiceSpecObjectRef objectRef;
        java.lang.String protocolType;
        java.lang.String mode;
        java.lang.Number numActivators;

        /**
         * Sets the value of {@link ServerlessServiceSpec#getObjectRef}
         * @param objectRef ObjectRef defines the resource that this ServerlessService is responsible for making "serverless". This parameter is required.
         * @return {@code this}
         */
        public Builder objectRef(imports.dev.knative.internal.networking.ServerlessServiceSpecObjectRef objectRef) {
            this.objectRef = objectRef;
            return this;
        }

        /**
         * Sets the value of {@link ServerlessServiceSpec#getProtocolType}
         * @param protocolType The application-layer protocol. This parameter is required.
         *                     Matches <code>RevisionProtocolType</code> set on the owning pa/revision.
         *                     serving imports networking, so just use string.
         * @return {@code this}
         */
        public Builder protocolType(java.lang.String protocolType) {
            this.protocolType = protocolType;
            return this;
        }

        /**
         * Sets the value of {@link ServerlessServiceSpec#getMode}
         * @param mode Mode describes the mode of operation of the ServerlessService.
         * @return {@code this}
         */
        public Builder mode(java.lang.String mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Sets the value of {@link ServerlessServiceSpec#getNumActivators}
         * @param numActivators NumActivators contains number of Activators that this revision should be assigned.
         *                      O means — assign all.
         * @return {@code this}
         */
        public Builder numActivators(java.lang.Number numActivators) {
            this.numActivators = numActivators;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ServerlessServiceSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ServerlessServiceSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ServerlessServiceSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ServerlessServiceSpec {
        private final imports.dev.knative.internal.networking.ServerlessServiceSpecObjectRef objectRef;
        private final java.lang.String protocolType;
        private final java.lang.String mode;
        private final java.lang.Number numActivators;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.objectRef = software.amazon.jsii.Kernel.get(this, "objectRef", software.amazon.jsii.NativeType.forClass(imports.dev.knative.internal.networking.ServerlessServiceSpecObjectRef.class));
            this.protocolType = software.amazon.jsii.Kernel.get(this, "protocolType", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.mode = software.amazon.jsii.Kernel.get(this, "mode", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.numActivators = software.amazon.jsii.Kernel.get(this, "numActivators", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.objectRef = java.util.Objects.requireNonNull(builder.objectRef, "objectRef is required");
            this.protocolType = java.util.Objects.requireNonNull(builder.protocolType, "protocolType is required");
            this.mode = builder.mode;
            this.numActivators = builder.numActivators;
        }

        @Override
        public final imports.dev.knative.internal.networking.ServerlessServiceSpecObjectRef getObjectRef() {
            return this.objectRef;
        }

        @Override
        public final java.lang.String getProtocolType() {
            return this.protocolType;
        }

        @Override
        public final java.lang.String getMode() {
            return this.mode;
        }

        @Override
        public final java.lang.Number getNumActivators() {
            return this.numActivators;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("objectRef", om.valueToTree(this.getObjectRef()));
            data.set("protocolType", om.valueToTree(this.getProtocolType()));
            if (this.getMode() != null) {
                data.set("mode", om.valueToTree(this.getMode()));
            }
            if (this.getNumActivators() != null) {
                data.set("numActivators", om.valueToTree(this.getNumActivators()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeinternalnetworking.ServerlessServiceSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ServerlessServiceSpec.Jsii$Proxy that = (ServerlessServiceSpec.Jsii$Proxy) o;

            if (!objectRef.equals(that.objectRef)) return false;
            if (!protocolType.equals(that.protocolType)) return false;
            if (this.mode != null ? !this.mode.equals(that.mode) : that.mode != null) return false;
            return this.numActivators != null ? this.numActivators.equals(that.numActivators) : that.numActivators == null;
        }

        @Override
        public final int hashCode() {
            int result = this.objectRef.hashCode();
            result = 31 * result + (this.protocolType.hashCode());
            result = 31 * result + (this.mode != null ? this.mode.hashCode() : 0);
            result = 31 * result + (this.numActivators != null ? this.numActivators.hashCode() : 0);
            return result;
        }
    }
}
