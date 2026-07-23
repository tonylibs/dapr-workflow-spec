package imports.io.dapr;

/**
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.464Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ResiliencySpecPolicies")
@software.amazon.jsii.Jsii.Proxy(ResiliencySpecPolicies.Jsii$Proxy.class)
public interface ResiliencySpecPolicies extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecPoliciesCircuitBreakers> getCircuitBreakers() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecPoliciesRetries> getRetries() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, java.lang.String> getTimeouts() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ResiliencySpecPolicies}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ResiliencySpecPolicies}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ResiliencySpecPolicies> {
        java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecPoliciesCircuitBreakers> circuitBreakers;
        java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecPoliciesRetries> retries;
        java.util.Map<java.lang.String, java.lang.String> timeouts;

        /**
         * Sets the value of {@link ResiliencySpecPolicies#getCircuitBreakers}
         * @param circuitBreakers the value to be set.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder circuitBreakers(java.util.Map<java.lang.String, ? extends imports.io.dapr.ResiliencySpecPoliciesCircuitBreakers> circuitBreakers) {
            this.circuitBreakers = (java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecPoliciesCircuitBreakers>)circuitBreakers;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpecPolicies#getRetries}
         * @param retries the value to be set.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder retries(java.util.Map<java.lang.String, ? extends imports.io.dapr.ResiliencySpecPoliciesRetries> retries) {
            this.retries = (java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecPoliciesRetries>)retries;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpecPolicies#getTimeouts}
         * @param timeouts the value to be set.
         * @return {@code this}
         */
        public Builder timeouts(java.util.Map<java.lang.String, java.lang.String> timeouts) {
            this.timeouts = timeouts;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ResiliencySpecPolicies}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ResiliencySpecPolicies build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ResiliencySpecPolicies}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ResiliencySpecPolicies {
        private final java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecPoliciesCircuitBreakers> circuitBreakers;
        private final java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecPoliciesRetries> retries;
        private final java.util.Map<java.lang.String, java.lang.String> timeouts;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.circuitBreakers = software.amazon.jsii.Kernel.get(this, "circuitBreakers", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ResiliencySpecPoliciesCircuitBreakers.class)));
            this.retries = software.amazon.jsii.Kernel.get(this, "retries", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ResiliencySpecPoliciesRetries.class)));
            this.timeouts = software.amazon.jsii.Kernel.get(this, "timeouts", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.circuitBreakers = (java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecPoliciesCircuitBreakers>)builder.circuitBreakers;
            this.retries = (java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecPoliciesRetries>)builder.retries;
            this.timeouts = builder.timeouts;
        }

        @Override
        public final java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecPoliciesCircuitBreakers> getCircuitBreakers() {
            return this.circuitBreakers;
        }

        @Override
        public final java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecPoliciesRetries> getRetries() {
            return this.retries;
        }

        @Override
        public final java.util.Map<java.lang.String, java.lang.String> getTimeouts() {
            return this.timeouts;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getCircuitBreakers() != null) {
                data.set("circuitBreakers", om.valueToTree(this.getCircuitBreakers()));
            }
            if (this.getRetries() != null) {
                data.set("retries", om.valueToTree(this.getRetries()));
            }
            if (this.getTimeouts() != null) {
                data.set("timeouts", om.valueToTree(this.getTimeouts()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ResiliencySpecPolicies"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ResiliencySpecPolicies.Jsii$Proxy that = (ResiliencySpecPolicies.Jsii$Proxy) o;

            if (this.circuitBreakers != null ? !this.circuitBreakers.equals(that.circuitBreakers) : that.circuitBreakers != null) return false;
            if (this.retries != null ? !this.retries.equals(that.retries) : that.retries != null) return false;
            return this.timeouts != null ? this.timeouts.equals(that.timeouts) : that.timeouts == null;
        }

        @Override
        public final int hashCode() {
            int result = this.circuitBreakers != null ? this.circuitBreakers.hashCode() : 0;
            result = 31 * result + (this.retries != null ? this.retries.hashCode() : 0);
            result = 31 * result + (this.timeouts != null ? this.timeouts.hashCode() : 0);
            return result;
        }
    }
}
