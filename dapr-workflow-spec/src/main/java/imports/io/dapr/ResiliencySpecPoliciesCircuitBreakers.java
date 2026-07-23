package imports.io.dapr;

/**
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.464Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ResiliencySpecPoliciesCircuitBreakers")
@software.amazon.jsii.Jsii.Proxy(ResiliencySpecPoliciesCircuitBreakers.Jsii$Proxy.class)
public interface ResiliencySpecPoliciesCircuitBreakers extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getInterval() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getMaxRequests() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getTimeout() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getTrip() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ResiliencySpecPoliciesCircuitBreakers}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ResiliencySpecPoliciesCircuitBreakers}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ResiliencySpecPoliciesCircuitBreakers> {
        java.lang.String interval;
        java.lang.Number maxRequests;
        java.lang.String timeout;
        java.lang.String trip;

        /**
         * Sets the value of {@link ResiliencySpecPoliciesCircuitBreakers#getInterval}
         * @param interval the value to be set.
         * @return {@code this}
         */
        public Builder interval(java.lang.String interval) {
            this.interval = interval;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpecPoliciesCircuitBreakers#getMaxRequests}
         * @param maxRequests the value to be set.
         * @return {@code this}
         */
        public Builder maxRequests(java.lang.Number maxRequests) {
            this.maxRequests = maxRequests;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpecPoliciesCircuitBreakers#getTimeout}
         * @param timeout the value to be set.
         * @return {@code this}
         */
        public Builder timeout(java.lang.String timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpecPoliciesCircuitBreakers#getTrip}
         * @param trip the value to be set.
         * @return {@code this}
         */
        public Builder trip(java.lang.String trip) {
            this.trip = trip;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ResiliencySpecPoliciesCircuitBreakers}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ResiliencySpecPoliciesCircuitBreakers build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ResiliencySpecPoliciesCircuitBreakers}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ResiliencySpecPoliciesCircuitBreakers {
        private final java.lang.String interval;
        private final java.lang.Number maxRequests;
        private final java.lang.String timeout;
        private final java.lang.String trip;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.interval = software.amazon.jsii.Kernel.get(this, "interval", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.maxRequests = software.amazon.jsii.Kernel.get(this, "maxRequests", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.timeout = software.amazon.jsii.Kernel.get(this, "timeout", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.trip = software.amazon.jsii.Kernel.get(this, "trip", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.interval = builder.interval;
            this.maxRequests = builder.maxRequests;
            this.timeout = builder.timeout;
            this.trip = builder.trip;
        }

        @Override
        public final java.lang.String getInterval() {
            return this.interval;
        }

        @Override
        public final java.lang.Number getMaxRequests() {
            return this.maxRequests;
        }

        @Override
        public final java.lang.String getTimeout() {
            return this.timeout;
        }

        @Override
        public final java.lang.String getTrip() {
            return this.trip;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getInterval() != null) {
                data.set("interval", om.valueToTree(this.getInterval()));
            }
            if (this.getMaxRequests() != null) {
                data.set("maxRequests", om.valueToTree(this.getMaxRequests()));
            }
            if (this.getTimeout() != null) {
                data.set("timeout", om.valueToTree(this.getTimeout()));
            }
            if (this.getTrip() != null) {
                data.set("trip", om.valueToTree(this.getTrip()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ResiliencySpecPoliciesCircuitBreakers"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ResiliencySpecPoliciesCircuitBreakers.Jsii$Proxy that = (ResiliencySpecPoliciesCircuitBreakers.Jsii$Proxy) o;

            if (this.interval != null ? !this.interval.equals(that.interval) : that.interval != null) return false;
            if (this.maxRequests != null ? !this.maxRequests.equals(that.maxRequests) : that.maxRequests != null) return false;
            if (this.timeout != null ? !this.timeout.equals(that.timeout) : that.timeout != null) return false;
            return this.trip != null ? this.trip.equals(that.trip) : that.trip == null;
        }

        @Override
        public final int hashCode() {
            int result = this.interval != null ? this.interval.hashCode() : 0;
            result = 31 * result + (this.maxRequests != null ? this.maxRequests.hashCode() : 0);
            result = 31 * result + (this.timeout != null ? this.timeout.hashCode() : 0);
            result = 31 * result + (this.trip != null ? this.trip.hashCode() : 0);
            return result;
        }
    }
}
