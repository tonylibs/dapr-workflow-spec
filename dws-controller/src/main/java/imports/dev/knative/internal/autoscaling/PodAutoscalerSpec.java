package imports.dev.knative.internal.autoscaling;

/**
 * Spec holds the desired state of the PodAutoscaler (from the client).
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:01:58.168Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.autoscaling.$Module.class, fqn = "devknativeinternalautoscaling.PodAutoscalerSpec")
@software.amazon.jsii.Jsii.Proxy(PodAutoscalerSpec.Jsii$Proxy.class)
public interface PodAutoscalerSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     * The application-layer protocol.
     * <p>
     * Matches <code>ProtocolType</code> inferred from the revision spec.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getProtocolType();

    /**
     * ScaleTargetRef defines the /scale-able resource that this PodAutoscaler is responsible for quickly right-sizing.
     */
    @org.jetbrains.annotations.NotNull imports.dev.knative.internal.autoscaling.PodAutoscalerSpecScaleTargetRef getScaleTargetRef();

    /**
     * ContainerConcurrency specifies the maximum allowed in-flight (concurrent) requests per container of the Revision.
     * <p>
     * Defaults to <code>0</code> which means unlimited concurrency.
     * <p>
     * Default: 0` which means unlimited concurrency.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getContainerConcurrency() {
        return null;
    }

    /**
     * Reachability specifies whether or not the <code>ScaleTargetRef</code> can be reached (ie.
     * <p>
     * has a route).
     * Defaults to <code>ReachabilityUnknown</code>
     * <p>
     * Default: ReachabilityUnknown`
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getReachability() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link PodAutoscalerSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link PodAutoscalerSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<PodAutoscalerSpec> {
        java.lang.String protocolType;
        imports.dev.knative.internal.autoscaling.PodAutoscalerSpecScaleTargetRef scaleTargetRef;
        java.lang.Number containerConcurrency;
        java.lang.String reachability;

        /**
         * Sets the value of {@link PodAutoscalerSpec#getProtocolType}
         * @param protocolType The application-layer protocol. This parameter is required.
         *                     Matches <code>ProtocolType</code> inferred from the revision spec.
         * @return {@code this}
         */
        public Builder protocolType(java.lang.String protocolType) {
            this.protocolType = protocolType;
            return this;
        }

        /**
         * Sets the value of {@link PodAutoscalerSpec#getScaleTargetRef}
         * @param scaleTargetRef ScaleTargetRef defines the /scale-able resource that this PodAutoscaler is responsible for quickly right-sizing. This parameter is required.
         * @return {@code this}
         */
        public Builder scaleTargetRef(imports.dev.knative.internal.autoscaling.PodAutoscalerSpecScaleTargetRef scaleTargetRef) {
            this.scaleTargetRef = scaleTargetRef;
            return this;
        }

        /**
         * Sets the value of {@link PodAutoscalerSpec#getContainerConcurrency}
         * @param containerConcurrency ContainerConcurrency specifies the maximum allowed in-flight (concurrent) requests per container of the Revision.
         *                             Defaults to <code>0</code> which means unlimited concurrency.
         * @return {@code this}
         */
        public Builder containerConcurrency(java.lang.Number containerConcurrency) {
            this.containerConcurrency = containerConcurrency;
            return this;
        }

        /**
         * Sets the value of {@link PodAutoscalerSpec#getReachability}
         * @param reachability Reachability specifies whether or not the <code>ScaleTargetRef</code> can be reached (ie.
         *                     has a route).
         *                     Defaults to <code>ReachabilityUnknown</code>
         * @return {@code this}
         */
        public Builder reachability(java.lang.String reachability) {
            this.reachability = reachability;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link PodAutoscalerSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public PodAutoscalerSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link PodAutoscalerSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements PodAutoscalerSpec {
        private final java.lang.String protocolType;
        private final imports.dev.knative.internal.autoscaling.PodAutoscalerSpecScaleTargetRef scaleTargetRef;
        private final java.lang.Number containerConcurrency;
        private final java.lang.String reachability;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.protocolType = software.amazon.jsii.Kernel.get(this, "protocolType", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.scaleTargetRef = software.amazon.jsii.Kernel.get(this, "scaleTargetRef", software.amazon.jsii.NativeType.forClass(imports.dev.knative.internal.autoscaling.PodAutoscalerSpecScaleTargetRef.class));
            this.containerConcurrency = software.amazon.jsii.Kernel.get(this, "containerConcurrency", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.reachability = software.amazon.jsii.Kernel.get(this, "reachability", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.protocolType = java.util.Objects.requireNonNull(builder.protocolType, "protocolType is required");
            this.scaleTargetRef = java.util.Objects.requireNonNull(builder.scaleTargetRef, "scaleTargetRef is required");
            this.containerConcurrency = builder.containerConcurrency;
            this.reachability = builder.reachability;
        }

        @Override
        public final java.lang.String getProtocolType() {
            return this.protocolType;
        }

        @Override
        public final imports.dev.knative.internal.autoscaling.PodAutoscalerSpecScaleTargetRef getScaleTargetRef() {
            return this.scaleTargetRef;
        }

        @Override
        public final java.lang.Number getContainerConcurrency() {
            return this.containerConcurrency;
        }

        @Override
        public final java.lang.String getReachability() {
            return this.reachability;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("protocolType", om.valueToTree(this.getProtocolType()));
            data.set("scaleTargetRef", om.valueToTree(this.getScaleTargetRef()));
            if (this.getContainerConcurrency() != null) {
                data.set("containerConcurrency", om.valueToTree(this.getContainerConcurrency()));
            }
            if (this.getReachability() != null) {
                data.set("reachability", om.valueToTree(this.getReachability()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeinternalautoscaling.PodAutoscalerSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PodAutoscalerSpec.Jsii$Proxy that = (PodAutoscalerSpec.Jsii$Proxy) o;

            if (!protocolType.equals(that.protocolType)) return false;
            if (!scaleTargetRef.equals(that.scaleTargetRef)) return false;
            if (this.containerConcurrency != null ? !this.containerConcurrency.equals(that.containerConcurrency) : that.containerConcurrency != null) return false;
            return this.reachability != null ? this.reachability.equals(that.reachability) : that.reachability == null;
        }

        @Override
        public final int hashCode() {
            int result = this.protocolType.hashCode();
            result = 31 * result + (this.scaleTargetRef.hashCode());
            result = 31 * result + (this.containerConcurrency != null ? this.containerConcurrency.hashCode() : 0);
            result = 31 * result + (this.reachability != null ? this.reachability.hashCode() : 0);
            return result;
        }
    }
}
