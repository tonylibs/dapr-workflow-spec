package imports.io.dapr;

/**
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.465Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ResiliencySpecTargetsComponentsOutbound")
@software.amazon.jsii.Jsii.Proxy(ResiliencySpecTargetsComponentsOutbound.Jsii$Proxy.class)
public interface ResiliencySpecTargetsComponentsOutbound extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getCircuitBreaker() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getRetry() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getTimeout() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ResiliencySpecTargetsComponentsOutbound}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ResiliencySpecTargetsComponentsOutbound}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ResiliencySpecTargetsComponentsOutbound> {
        java.lang.String circuitBreaker;
        java.lang.String retry;
        java.lang.String timeout;

        /**
         * Sets the value of {@link ResiliencySpecTargetsComponentsOutbound#getCircuitBreaker}
         * @param circuitBreaker the value to be set.
         * @return {@code this}
         */
        public Builder circuitBreaker(java.lang.String circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpecTargetsComponentsOutbound#getRetry}
         * @param retry the value to be set.
         * @return {@code this}
         */
        public Builder retry(java.lang.String retry) {
            this.retry = retry;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpecTargetsComponentsOutbound#getTimeout}
         * @param timeout the value to be set.
         * @return {@code this}
         */
        public Builder timeout(java.lang.String timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ResiliencySpecTargetsComponentsOutbound}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ResiliencySpecTargetsComponentsOutbound build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ResiliencySpecTargetsComponentsOutbound}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ResiliencySpecTargetsComponentsOutbound {
        private final java.lang.String circuitBreaker;
        private final java.lang.String retry;
        private final java.lang.String timeout;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.circuitBreaker = software.amazon.jsii.Kernel.get(this, "circuitBreaker", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.retry = software.amazon.jsii.Kernel.get(this, "retry", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.timeout = software.amazon.jsii.Kernel.get(this, "timeout", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.circuitBreaker = builder.circuitBreaker;
            this.retry = builder.retry;
            this.timeout = builder.timeout;
        }

        @Override
        public final java.lang.String getCircuitBreaker() {
            return this.circuitBreaker;
        }

        @Override
        public final java.lang.String getRetry() {
            return this.retry;
        }

        @Override
        public final java.lang.String getTimeout() {
            return this.timeout;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getCircuitBreaker() != null) {
                data.set("circuitBreaker", om.valueToTree(this.getCircuitBreaker()));
            }
            if (this.getRetry() != null) {
                data.set("retry", om.valueToTree(this.getRetry()));
            }
            if (this.getTimeout() != null) {
                data.set("timeout", om.valueToTree(this.getTimeout()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ResiliencySpecTargetsComponentsOutbound"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ResiliencySpecTargetsComponentsOutbound.Jsii$Proxy that = (ResiliencySpecTargetsComponentsOutbound.Jsii$Proxy) o;

            if (this.circuitBreaker != null ? !this.circuitBreaker.equals(that.circuitBreaker) : that.circuitBreaker != null) return false;
            if (this.retry != null ? !this.retry.equals(that.retry) : that.retry != null) return false;
            return this.timeout != null ? this.timeout.equals(that.timeout) : that.timeout == null;
        }

        @Override
        public final int hashCode() {
            int result = this.circuitBreaker != null ? this.circuitBreaker.hashCode() : 0;
            result = 31 * result + (this.retry != null ? this.retry.hashCode() : 0);
            result = 31 * result + (this.timeout != null ? this.timeout.hashCode() : 0);
            return result;
        }
    }
}
