package imports.io.dapr;

/**
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.464Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ResiliencySpecPoliciesRetries")
@software.amazon.jsii.Jsii.Proxy(ResiliencySpecPoliciesRetries.Jsii$Proxy.class)
public interface ResiliencySpecPoliciesRetries extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getDuration() {
        return null;
    }

    /**
     * RetryMatching represents the rules to trigger retry in specific scenarios.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ResiliencySpecPoliciesRetriesMatching getMatching() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getMaxInterval() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getMaxRetries() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getPolicy() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ResiliencySpecPoliciesRetries}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ResiliencySpecPoliciesRetries}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ResiliencySpecPoliciesRetries> {
        java.lang.String duration;
        imports.io.dapr.ResiliencySpecPoliciesRetriesMatching matching;
        java.lang.String maxInterval;
        java.lang.Number maxRetries;
        java.lang.String policy;

        /**
         * Sets the value of {@link ResiliencySpecPoliciesRetries#getDuration}
         * @param duration the value to be set.
         * @return {@code this}
         */
        public Builder duration(java.lang.String duration) {
            this.duration = duration;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpecPoliciesRetries#getMatching}
         * @param matching RetryMatching represents the rules to trigger retry in specific scenarios.
         * @return {@code this}
         */
        public Builder matching(imports.io.dapr.ResiliencySpecPoliciesRetriesMatching matching) {
            this.matching = matching;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpecPoliciesRetries#getMaxInterval}
         * @param maxInterval the value to be set.
         * @return {@code this}
         */
        public Builder maxInterval(java.lang.String maxInterval) {
            this.maxInterval = maxInterval;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpecPoliciesRetries#getMaxRetries}
         * @param maxRetries the value to be set.
         * @return {@code this}
         */
        public Builder maxRetries(java.lang.Number maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpecPoliciesRetries#getPolicy}
         * @param policy the value to be set.
         * @return {@code this}
         */
        public Builder policy(java.lang.String policy) {
            this.policy = policy;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ResiliencySpecPoliciesRetries}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ResiliencySpecPoliciesRetries build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ResiliencySpecPoliciesRetries}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ResiliencySpecPoliciesRetries {
        private final java.lang.String duration;
        private final imports.io.dapr.ResiliencySpecPoliciesRetriesMatching matching;
        private final java.lang.String maxInterval;
        private final java.lang.Number maxRetries;
        private final java.lang.String policy;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.duration = software.amazon.jsii.Kernel.get(this, "duration", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.matching = software.amazon.jsii.Kernel.get(this, "matching", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ResiliencySpecPoliciesRetriesMatching.class));
            this.maxInterval = software.amazon.jsii.Kernel.get(this, "maxInterval", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.maxRetries = software.amazon.jsii.Kernel.get(this, "maxRetries", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.policy = software.amazon.jsii.Kernel.get(this, "policy", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.duration = builder.duration;
            this.matching = builder.matching;
            this.maxInterval = builder.maxInterval;
            this.maxRetries = builder.maxRetries;
            this.policy = builder.policy;
        }

        @Override
        public final java.lang.String getDuration() {
            return this.duration;
        }

        @Override
        public final imports.io.dapr.ResiliencySpecPoliciesRetriesMatching getMatching() {
            return this.matching;
        }

        @Override
        public final java.lang.String getMaxInterval() {
            return this.maxInterval;
        }

        @Override
        public final java.lang.Number getMaxRetries() {
            return this.maxRetries;
        }

        @Override
        public final java.lang.String getPolicy() {
            return this.policy;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getDuration() != null) {
                data.set("duration", om.valueToTree(this.getDuration()));
            }
            if (this.getMatching() != null) {
                data.set("matching", om.valueToTree(this.getMatching()));
            }
            if (this.getMaxInterval() != null) {
                data.set("maxInterval", om.valueToTree(this.getMaxInterval()));
            }
            if (this.getMaxRetries() != null) {
                data.set("maxRetries", om.valueToTree(this.getMaxRetries()));
            }
            if (this.getPolicy() != null) {
                data.set("policy", om.valueToTree(this.getPolicy()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ResiliencySpecPoliciesRetries"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ResiliencySpecPoliciesRetries.Jsii$Proxy that = (ResiliencySpecPoliciesRetries.Jsii$Proxy) o;

            if (this.duration != null ? !this.duration.equals(that.duration) : that.duration != null) return false;
            if (this.matching != null ? !this.matching.equals(that.matching) : that.matching != null) return false;
            if (this.maxInterval != null ? !this.maxInterval.equals(that.maxInterval) : that.maxInterval != null) return false;
            if (this.maxRetries != null ? !this.maxRetries.equals(that.maxRetries) : that.maxRetries != null) return false;
            return this.policy != null ? this.policy.equals(that.policy) : that.policy == null;
        }

        @Override
        public final int hashCode() {
            int result = this.duration != null ? this.duration.hashCode() : 0;
            result = 31 * result + (this.matching != null ? this.matching.hashCode() : 0);
            result = 31 * result + (this.maxInterval != null ? this.maxInterval.hashCode() : 0);
            result = 31 * result + (this.maxRetries != null ? this.maxRetries.hashCode() : 0);
            result = 31 * result + (this.policy != null ? this.policy.hashCode() : 0);
            return result;
        }
    }
}
