package imports.dev.knative.serving;

/**
 * Service acts as a top-level container that manages a Route and Configuration which implement a network service.
 * <p>
 * Service exists to provide a singular
 * abstraction which can be access controlled, reasoned about, and which
 * encapsulates software lifecycle decisions such as rollout policy and
 * team resource ownership. Service acts only as an orchestrator of the
 * underlying Routes and Configurations (much as a kubernetes Deployment
 * orchestrates ReplicaSets), and its usage is optional but recommended.
 * <p>
 * The Service's controller will track the statuses of its owned Configuration
 * and Route, reflecting their statuses and conditions as its own.
 * <p>
 * See also: https://github.com/knative/serving/blob/main/docs/spec/overview.md#service
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.173Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ServiceProps")
@software.amazon.jsii.Jsii.Proxy(ServiceProps.Jsii$Proxy.class)
public interface ServiceProps extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    default @org.jetbrains.annotations.Nullable org.cdk8s.ApiObjectMetadata getMetadata() {
        return null;
    }

    /**
     * ServiceSpec represents the configuration for the Service object.
     * <p>
     * A Service's specification is the union of the specifications for a Route
     * and Configuration.  The Service restricts what can be expressed in these
     * fields, e.g. the Route must reference the provided Configuration;
     * however, these limitations also enable friendlier defaulting,
     * e.g. Route never needs a Configuration name, and may be defaulted to
     * the appropriate "run latest" spec.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ServiceSpec getSpec() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ServiceProps}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ServiceProps}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ServiceProps> {
        org.cdk8s.ApiObjectMetadata metadata;
        imports.dev.knative.serving.ServiceSpec spec;

        /**
         * Sets the value of {@link ServiceProps#getMetadata}
         * @param metadata the value to be set.
         * @return {@code this}
         */
        public Builder metadata(org.cdk8s.ApiObjectMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Sets the value of {@link ServiceProps#getSpec}
         * @param spec ServiceSpec represents the configuration for the Service object.
         *             A Service's specification is the union of the specifications for a Route
         *             and Configuration.  The Service restricts what can be expressed in these
         *             fields, e.g. the Route must reference the provided Configuration;
         *             however, these limitations also enable friendlier defaulting,
         *             e.g. Route never needs a Configuration name, and may be defaulted to
         *             the appropriate "run latest" spec.
         * @return {@code this}
         */
        public Builder spec(imports.dev.knative.serving.ServiceSpec spec) {
            this.spec = spec;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ServiceProps}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ServiceProps build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ServiceProps}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ServiceProps {
        private final org.cdk8s.ApiObjectMetadata metadata;
        private final imports.dev.knative.serving.ServiceSpec spec;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.metadata = software.amazon.jsii.Kernel.get(this, "metadata", software.amazon.jsii.NativeType.forClass(org.cdk8s.ApiObjectMetadata.class));
            this.spec = software.amazon.jsii.Kernel.get(this, "spec", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpec.class));
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
        public final imports.dev.knative.serving.ServiceSpec getSpec() {
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
            struct.set("fqn", om.valueToTree("devknativeserving.ServiceProps"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ServiceProps.Jsii$Proxy that = (ServiceProps.Jsii$Proxy) o;

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
