package imports.io.dapr;

/**
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.464Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ResiliencySpec")
@software.amazon.jsii.Jsii.Proxy(ResiliencySpec.Jsii$Proxy.class)
public interface ResiliencySpec extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull imports.io.dapr.ResiliencySpecPolicies getPolicies();

    /**
     */
    @org.jetbrains.annotations.NotNull imports.io.dapr.ResiliencySpecTargets getTargets();

    /**
     * @return a {@link Builder} of {@link ResiliencySpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ResiliencySpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ResiliencySpec> {
        imports.io.dapr.ResiliencySpecPolicies policies;
        imports.io.dapr.ResiliencySpecTargets targets;

        /**
         * Sets the value of {@link ResiliencySpec#getPolicies}
         * @param policies the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder policies(imports.io.dapr.ResiliencySpecPolicies policies) {
            this.policies = policies;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpec#getTargets}
         * @param targets the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder targets(imports.io.dapr.ResiliencySpecTargets targets) {
            this.targets = targets;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ResiliencySpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ResiliencySpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ResiliencySpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ResiliencySpec {
        private final imports.io.dapr.ResiliencySpecPolicies policies;
        private final imports.io.dapr.ResiliencySpecTargets targets;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.policies = software.amazon.jsii.Kernel.get(this, "policies", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ResiliencySpecPolicies.class));
            this.targets = software.amazon.jsii.Kernel.get(this, "targets", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ResiliencySpecTargets.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.policies = java.util.Objects.requireNonNull(builder.policies, "policies is required");
            this.targets = java.util.Objects.requireNonNull(builder.targets, "targets is required");
        }

        @Override
        public final imports.io.dapr.ResiliencySpecPolicies getPolicies() {
            return this.policies;
        }

        @Override
        public final imports.io.dapr.ResiliencySpecTargets getTargets() {
            return this.targets;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("policies", om.valueToTree(this.getPolicies()));
            data.set("targets", om.valueToTree(this.getTargets()));

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ResiliencySpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ResiliencySpec.Jsii$Proxy that = (ResiliencySpec.Jsii$Proxy) o;

            if (!policies.equals(that.policies)) return false;
            return this.targets.equals(that.targets);
        }

        @Override
        public final int hashCode() {
            int result = this.policies.hashCode();
            result = 31 * result + (this.targets.hashCode());
            return result;
        }
    }
}
