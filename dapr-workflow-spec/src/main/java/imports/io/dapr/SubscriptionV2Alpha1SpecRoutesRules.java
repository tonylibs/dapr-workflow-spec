package imports.io.dapr;

/**
 * Rule is used to specify the condition for sending a message to a specific path.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.466Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.SubscriptionV2Alpha1SpecRoutesRules")
@software.amazon.jsii.Jsii.Proxy(SubscriptionV2Alpha1SpecRoutesRules.Jsii$Proxy.class)
public interface SubscriptionV2Alpha1SpecRoutesRules extends software.amazon.jsii.JsiiSerializable {

    /**
     * The optional CEL expression used to match the event.
     * <p>
     * If the match is not specified, then the route is considered
     * the default. The rules are tested in the order specified,
     * so they should be define from most-to-least specific.
     * The default route should appear last in the list.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getMatch();

    /**
     * The path for events that match this rule.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getPath();

    /**
     * @return a {@link Builder} of {@link SubscriptionV2Alpha1SpecRoutesRules}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link SubscriptionV2Alpha1SpecRoutesRules}
     */
    public static final class Builder implements software.amazon.jsii.Builder<SubscriptionV2Alpha1SpecRoutesRules> {
        java.lang.String match;
        java.lang.String path;

        /**
         * Sets the value of {@link SubscriptionV2Alpha1SpecRoutesRules#getMatch}
         * @param match The optional CEL expression used to match the event. This parameter is required.
         *              If the match is not specified, then the route is considered
         *              the default. The rules are tested in the order specified,
         *              so they should be define from most-to-least specific.
         *              The default route should appear last in the list.
         * @return {@code this}
         */
        public Builder match(java.lang.String match) {
            this.match = match;
            return this;
        }

        /**
         * Sets the value of {@link SubscriptionV2Alpha1SpecRoutesRules#getPath}
         * @param path The path for events that match this rule. This parameter is required.
         * @return {@code this}
         */
        public Builder path(java.lang.String path) {
            this.path = path;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link SubscriptionV2Alpha1SpecRoutesRules}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public SubscriptionV2Alpha1SpecRoutesRules build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link SubscriptionV2Alpha1SpecRoutesRules}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements SubscriptionV2Alpha1SpecRoutesRules {
        private final java.lang.String match;
        private final java.lang.String path;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.match = software.amazon.jsii.Kernel.get(this, "match", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.path = software.amazon.jsii.Kernel.get(this, "path", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.match = java.util.Objects.requireNonNull(builder.match, "match is required");
            this.path = java.util.Objects.requireNonNull(builder.path, "path is required");
        }

        @Override
        public final java.lang.String getMatch() {
            return this.match;
        }

        @Override
        public final java.lang.String getPath() {
            return this.path;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("match", om.valueToTree(this.getMatch()));
            data.set("path", om.valueToTree(this.getPath()));

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.SubscriptionV2Alpha1SpecRoutesRules"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SubscriptionV2Alpha1SpecRoutesRules.Jsii$Proxy that = (SubscriptionV2Alpha1SpecRoutesRules.Jsii$Proxy) o;

            if (!match.equals(that.match)) return false;
            return this.path.equals(that.path);
        }

        @Override
        public final int hashCode() {
            int result = this.match.hashCode();
            result = 31 * result + (this.path.hashCode());
            return result;
        }
    }
}
