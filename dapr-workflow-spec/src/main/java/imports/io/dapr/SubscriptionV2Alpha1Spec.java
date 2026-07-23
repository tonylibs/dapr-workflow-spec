package imports.io.dapr;

/**
 * SubscriptionSpec is the spec for an event subscription.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.466Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.SubscriptionV2Alpha1Spec")
@software.amazon.jsii.Jsii.Proxy(SubscriptionV2Alpha1Spec.Jsii$Proxy.class)
public interface SubscriptionV2Alpha1Spec extends software.amazon.jsii.JsiiSerializable {

    /**
     * The PubSub component name.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getPubsubname();

    /**
     * The Routes configuration for this topic.
     */
    @org.jetbrains.annotations.NotNull imports.io.dapr.SubscriptionV2Alpha1SpecRoutes getRoutes();

    /**
     * The topic name to subscribe to.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getTopic();

    /**
     * The option to enable bulk subscription for this topic.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.SubscriptionV2Alpha1SpecBulkSubscribe getBulkSubscribe() {
        return null;
    }

    /**
     * The optional dead letter queue for this topic to send events to.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getDeadLetterTopic() {
        return null;
    }

    /**
     * The optional metadata to provide the subscription.
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, java.lang.String> getMetadata() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link SubscriptionV2Alpha1Spec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link SubscriptionV2Alpha1Spec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<SubscriptionV2Alpha1Spec> {
        java.lang.String pubsubname;
        imports.io.dapr.SubscriptionV2Alpha1SpecRoutes routes;
        java.lang.String topic;
        imports.io.dapr.SubscriptionV2Alpha1SpecBulkSubscribe bulkSubscribe;
        java.lang.String deadLetterTopic;
        java.util.Map<java.lang.String, java.lang.String> metadata;

        /**
         * Sets the value of {@link SubscriptionV2Alpha1Spec#getPubsubname}
         * @param pubsubname The PubSub component name. This parameter is required.
         * @return {@code this}
         */
        public Builder pubsubname(java.lang.String pubsubname) {
            this.pubsubname = pubsubname;
            return this;
        }

        /**
         * Sets the value of {@link SubscriptionV2Alpha1Spec#getRoutes}
         * @param routes The Routes configuration for this topic. This parameter is required.
         * @return {@code this}
         */
        public Builder routes(imports.io.dapr.SubscriptionV2Alpha1SpecRoutes routes) {
            this.routes = routes;
            return this;
        }

        /**
         * Sets the value of {@link SubscriptionV2Alpha1Spec#getTopic}
         * @param topic The topic name to subscribe to. This parameter is required.
         * @return {@code this}
         */
        public Builder topic(java.lang.String topic) {
            this.topic = topic;
            return this;
        }

        /**
         * Sets the value of {@link SubscriptionV2Alpha1Spec#getBulkSubscribe}
         * @param bulkSubscribe The option to enable bulk subscription for this topic.
         * @return {@code this}
         */
        public Builder bulkSubscribe(imports.io.dapr.SubscriptionV2Alpha1SpecBulkSubscribe bulkSubscribe) {
            this.bulkSubscribe = bulkSubscribe;
            return this;
        }

        /**
         * Sets the value of {@link SubscriptionV2Alpha1Spec#getDeadLetterTopic}
         * @param deadLetterTopic The optional dead letter queue for this topic to send events to.
         * @return {@code this}
         */
        public Builder deadLetterTopic(java.lang.String deadLetterTopic) {
            this.deadLetterTopic = deadLetterTopic;
            return this;
        }

        /**
         * Sets the value of {@link SubscriptionV2Alpha1Spec#getMetadata}
         * @param metadata The optional metadata to provide the subscription.
         * @return {@code this}
         */
        public Builder metadata(java.util.Map<java.lang.String, java.lang.String> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link SubscriptionV2Alpha1Spec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public SubscriptionV2Alpha1Spec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link SubscriptionV2Alpha1Spec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements SubscriptionV2Alpha1Spec {
        private final java.lang.String pubsubname;
        private final imports.io.dapr.SubscriptionV2Alpha1SpecRoutes routes;
        private final java.lang.String topic;
        private final imports.io.dapr.SubscriptionV2Alpha1SpecBulkSubscribe bulkSubscribe;
        private final java.lang.String deadLetterTopic;
        private final java.util.Map<java.lang.String, java.lang.String> metadata;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.pubsubname = software.amazon.jsii.Kernel.get(this, "pubsubname", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.routes = software.amazon.jsii.Kernel.get(this, "routes", software.amazon.jsii.NativeType.forClass(imports.io.dapr.SubscriptionV2Alpha1SpecRoutes.class));
            this.topic = software.amazon.jsii.Kernel.get(this, "topic", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.bulkSubscribe = software.amazon.jsii.Kernel.get(this, "bulkSubscribe", software.amazon.jsii.NativeType.forClass(imports.io.dapr.SubscriptionV2Alpha1SpecBulkSubscribe.class));
            this.deadLetterTopic = software.amazon.jsii.Kernel.get(this, "deadLetterTopic", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.metadata = software.amazon.jsii.Kernel.get(this, "metadata", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.pubsubname = java.util.Objects.requireNonNull(builder.pubsubname, "pubsubname is required");
            this.routes = java.util.Objects.requireNonNull(builder.routes, "routes is required");
            this.topic = java.util.Objects.requireNonNull(builder.topic, "topic is required");
            this.bulkSubscribe = builder.bulkSubscribe;
            this.deadLetterTopic = builder.deadLetterTopic;
            this.metadata = builder.metadata;
        }

        @Override
        public final java.lang.String getPubsubname() {
            return this.pubsubname;
        }

        @Override
        public final imports.io.dapr.SubscriptionV2Alpha1SpecRoutes getRoutes() {
            return this.routes;
        }

        @Override
        public final java.lang.String getTopic() {
            return this.topic;
        }

        @Override
        public final imports.io.dapr.SubscriptionV2Alpha1SpecBulkSubscribe getBulkSubscribe() {
            return this.bulkSubscribe;
        }

        @Override
        public final java.lang.String getDeadLetterTopic() {
            return this.deadLetterTopic;
        }

        @Override
        public final java.util.Map<java.lang.String, java.lang.String> getMetadata() {
            return this.metadata;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("pubsubname", om.valueToTree(this.getPubsubname()));
            data.set("routes", om.valueToTree(this.getRoutes()));
            data.set("topic", om.valueToTree(this.getTopic()));
            if (this.getBulkSubscribe() != null) {
                data.set("bulkSubscribe", om.valueToTree(this.getBulkSubscribe()));
            }
            if (this.getDeadLetterTopic() != null) {
                data.set("deadLetterTopic", om.valueToTree(this.getDeadLetterTopic()));
            }
            if (this.getMetadata() != null) {
                data.set("metadata", om.valueToTree(this.getMetadata()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.SubscriptionV2Alpha1Spec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SubscriptionV2Alpha1Spec.Jsii$Proxy that = (SubscriptionV2Alpha1Spec.Jsii$Proxy) o;

            if (!pubsubname.equals(that.pubsubname)) return false;
            if (!routes.equals(that.routes)) return false;
            if (!topic.equals(that.topic)) return false;
            if (this.bulkSubscribe != null ? !this.bulkSubscribe.equals(that.bulkSubscribe) : that.bulkSubscribe != null) return false;
            if (this.deadLetterTopic != null ? !this.deadLetterTopic.equals(that.deadLetterTopic) : that.deadLetterTopic != null) return false;
            return this.metadata != null ? this.metadata.equals(that.metadata) : that.metadata == null;
        }

        @Override
        public final int hashCode() {
            int result = this.pubsubname.hashCode();
            result = 31 * result + (this.routes.hashCode());
            result = 31 * result + (this.topic.hashCode());
            result = 31 * result + (this.bulkSubscribe != null ? this.bulkSubscribe.hashCode() : 0);
            result = 31 * result + (this.deadLetterTopic != null ? this.deadLetterTopic.hashCode() : 0);
            result = 31 * result + (this.metadata != null ? this.metadata.hashCode() : 0);
            return result;
        }
    }
}
