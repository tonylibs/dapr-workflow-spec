package imports.io.dapr;

/**
 * BulkSubscribe encapsulates the bulk subscription configuration for a topic.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.466Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.SubscriptionSpecBulkSubscribe")
@software.amazon.jsii.Jsii.Proxy(SubscriptionSpecBulkSubscribe.Jsii$Proxy.class)
public interface SubscriptionSpecBulkSubscribe extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.Boolean getEnabled();

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getMaxAwaitDurationMs() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getMaxMessagesCount() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link SubscriptionSpecBulkSubscribe}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link SubscriptionSpecBulkSubscribe}
     */
    public static final class Builder implements software.amazon.jsii.Builder<SubscriptionSpecBulkSubscribe> {
        java.lang.Boolean enabled;
        java.lang.Number maxAwaitDurationMs;
        java.lang.Number maxMessagesCount;

        /**
         * Sets the value of {@link SubscriptionSpecBulkSubscribe#getEnabled}
         * @param enabled the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder enabled(java.lang.Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Sets the value of {@link SubscriptionSpecBulkSubscribe#getMaxAwaitDurationMs}
         * @param maxAwaitDurationMs the value to be set.
         * @return {@code this}
         */
        public Builder maxAwaitDurationMs(java.lang.Number maxAwaitDurationMs) {
            this.maxAwaitDurationMs = maxAwaitDurationMs;
            return this;
        }

        /**
         * Sets the value of {@link SubscriptionSpecBulkSubscribe#getMaxMessagesCount}
         * @param maxMessagesCount the value to be set.
         * @return {@code this}
         */
        public Builder maxMessagesCount(java.lang.Number maxMessagesCount) {
            this.maxMessagesCount = maxMessagesCount;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link SubscriptionSpecBulkSubscribe}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public SubscriptionSpecBulkSubscribe build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link SubscriptionSpecBulkSubscribe}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements SubscriptionSpecBulkSubscribe {
        private final java.lang.Boolean enabled;
        private final java.lang.Number maxAwaitDurationMs;
        private final java.lang.Number maxMessagesCount;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.enabled = software.amazon.jsii.Kernel.get(this, "enabled", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.maxAwaitDurationMs = software.amazon.jsii.Kernel.get(this, "maxAwaitDurationMs", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.maxMessagesCount = software.amazon.jsii.Kernel.get(this, "maxMessagesCount", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.enabled = java.util.Objects.requireNonNull(builder.enabled, "enabled is required");
            this.maxAwaitDurationMs = builder.maxAwaitDurationMs;
            this.maxMessagesCount = builder.maxMessagesCount;
        }

        @Override
        public final java.lang.Boolean getEnabled() {
            return this.enabled;
        }

        @Override
        public final java.lang.Number getMaxAwaitDurationMs() {
            return this.maxAwaitDurationMs;
        }

        @Override
        public final java.lang.Number getMaxMessagesCount() {
            return this.maxMessagesCount;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("enabled", om.valueToTree(this.getEnabled()));
            if (this.getMaxAwaitDurationMs() != null) {
                data.set("maxAwaitDurationMs", om.valueToTree(this.getMaxAwaitDurationMs()));
            }
            if (this.getMaxMessagesCount() != null) {
                data.set("maxMessagesCount", om.valueToTree(this.getMaxMessagesCount()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.SubscriptionSpecBulkSubscribe"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SubscriptionSpecBulkSubscribe.Jsii$Proxy that = (SubscriptionSpecBulkSubscribe.Jsii$Proxy) o;

            if (!enabled.equals(that.enabled)) return false;
            if (this.maxAwaitDurationMs != null ? !this.maxAwaitDurationMs.equals(that.maxAwaitDurationMs) : that.maxAwaitDurationMs != null) return false;
            return this.maxMessagesCount != null ? this.maxMessagesCount.equals(that.maxMessagesCount) : that.maxMessagesCount == null;
        }

        @Override
        public final int hashCode() {
            int result = this.enabled.hashCode();
            result = 31 * result + (this.maxAwaitDurationMs != null ? this.maxAwaitDurationMs.hashCode() : 0);
            result = 31 * result + (this.maxMessagesCount != null ? this.maxMessagesCount.hashCode() : 0);
            return result;
        }
    }
}
