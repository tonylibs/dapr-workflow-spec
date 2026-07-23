package imports.dev.knative.internal.autoscaling;

/**
 * PodAutoscaler is a Knative abstraction that encapsulates the interface by which Knative components instantiate autoscalers.
 * <p>
 * This definition is an abstraction that may be backed
 * by multiple definitions.  For more information, see the Knative Pluggability presentation:
 * https://docs.google.com/presentation/d/19vW9HFZ6Puxt31biNZF3uLRejDmu82rxJIk1cWmxF7w/edit
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:01:58.168Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.autoscaling.$Module.class, fqn = "devknativeinternalautoscaling.PodAutoscalerProps")
@software.amazon.jsii.Jsii.Proxy(PodAutoscalerProps.Jsii$Proxy.class)
public interface PodAutoscalerProps extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    default @org.jetbrains.annotations.Nullable org.cdk8s.ApiObjectMetadata getMetadata() {
        return null;
    }

    /**
     * Spec holds the desired state of the PodAutoscaler (from the client).
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.internal.autoscaling.PodAutoscalerSpec getSpec() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link PodAutoscalerProps}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link PodAutoscalerProps}
     */
    public static final class Builder implements software.amazon.jsii.Builder<PodAutoscalerProps> {
        org.cdk8s.ApiObjectMetadata metadata;
        imports.dev.knative.internal.autoscaling.PodAutoscalerSpec spec;

        /**
         * Sets the value of {@link PodAutoscalerProps#getMetadata}
         * @param metadata the value to be set.
         * @return {@code this}
         */
        public Builder metadata(org.cdk8s.ApiObjectMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Sets the value of {@link PodAutoscalerProps#getSpec}
         * @param spec Spec holds the desired state of the PodAutoscaler (from the client).
         * @return {@code this}
         */
        public Builder spec(imports.dev.knative.internal.autoscaling.PodAutoscalerSpec spec) {
            this.spec = spec;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link PodAutoscalerProps}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public PodAutoscalerProps build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link PodAutoscalerProps}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements PodAutoscalerProps {
        private final org.cdk8s.ApiObjectMetadata metadata;
        private final imports.dev.knative.internal.autoscaling.PodAutoscalerSpec spec;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.metadata = software.amazon.jsii.Kernel.get(this, "metadata", software.amazon.jsii.NativeType.forClass(org.cdk8s.ApiObjectMetadata.class));
            this.spec = software.amazon.jsii.Kernel.get(this, "spec", software.amazon.jsii.NativeType.forClass(imports.dev.knative.internal.autoscaling.PodAutoscalerSpec.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.metadata = builder.metadata;
            this.spec = builder.spec;
        }

        @Override
        public final org.cdk8s.ApiObjectMetadata getMetadata() {
            return this.metadata;
        }

        @Override
        public final imports.dev.knative.internal.autoscaling.PodAutoscalerSpec getSpec() {
            return this.spec;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getMetadata() != null) {
                data.set("metadata", om.valueToTree(this.getMetadata()));
            }
            if (this.getSpec() != null) {
                data.set("spec", om.valueToTree(this.getSpec()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeinternalautoscaling.PodAutoscalerProps"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PodAutoscalerProps.Jsii$Proxy that = (PodAutoscalerProps.Jsii$Proxy) o;

            if (this.metadata != null ? !this.metadata.equals(that.metadata) : that.metadata != null) return false;
            return this.spec != null ? this.spec.equals(that.spec) : that.spec == null;
        }

        @Override
        public final int hashCode() {
            int result = this.metadata != null ? this.metadata.hashCode() : 0;
            result = 31 * result + (this.spec != null ? this.spec.hashCode() : 0);
            return result;
        }
    }
}
