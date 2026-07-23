package imports.io.dapr;

/**
 * The Routes configuration for this topic.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.466Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.SubscriptionV2Alpha1SpecRoutes")
@software.amazon.jsii.Jsii.Proxy(SubscriptionV2Alpha1SpecRoutes.Jsii$Proxy.class)
public interface SubscriptionV2Alpha1SpecRoutes extends software.amazon.jsii.JsiiSerializable {

    /**
     * The default path for this topic.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getDefaultValue() {
        return null;
    }

    /**
     * The list of rules for this topic.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.SubscriptionV2Alpha1SpecRoutesRules> getRules() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link SubscriptionV2Alpha1SpecRoutes}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link SubscriptionV2Alpha1SpecRoutes}
     */
    public static final class Builder implements software.amazon.jsii.Builder<SubscriptionV2Alpha1SpecRoutes> {
        java.lang.String defaultValue;
        java.util.List<imports.io.dapr.SubscriptionV2Alpha1SpecRoutesRules> rules;

        /**
         * Sets the value of {@link SubscriptionV2Alpha1SpecRoutes#getDefaultValue}
         * @param defaultValue The default path for this topic.
         * @return {@code this}
         */
        public Builder defaultValue(java.lang.String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /**
         * Sets the value of {@link SubscriptionV2Alpha1SpecRoutes#getRules}
         * @param rules The list of rules for this topic.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder rules(java.util.List<? extends imports.io.dapr.SubscriptionV2Alpha1SpecRoutesRules> rules) {
            this.rules = (java.util.List<imports.io.dapr.SubscriptionV2Alpha1SpecRoutesRules>)rules;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link SubscriptionV2Alpha1SpecRoutes}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public SubscriptionV2Alpha1SpecRoutes build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link SubscriptionV2Alpha1SpecRoutes}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements SubscriptionV2Alpha1SpecRoutes {
        private final java.lang.String defaultValue;
        private final java.util.List<imports.io.dapr.SubscriptionV2Alpha1SpecRoutesRules> rules;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.defaultValue = software.amazon.jsii.Kernel.get(this, "default", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.rules = software.amazon.jsii.Kernel.get(this, "rules", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.SubscriptionV2Alpha1SpecRoutesRules.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.defaultValue = builder.defaultValue;
            this.rules = (java.util.List<imports.io.dapr.SubscriptionV2Alpha1SpecRoutesRules>)builder.rules;
        }

        @Override
        public final java.lang.String getDefaultValue() {
            return this.defaultValue;
        }

        @Override
        public final java.util.List<imports.io.dapr.SubscriptionV2Alpha1SpecRoutesRules> getRules() {
            return this.rules;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getDefaultValue() != null) {
                data.set("default", om.valueToTree(this.getDefaultValue()));
            }
            if (this.getRules() != null) {
                data.set("rules", om.valueToTree(this.getRules()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.SubscriptionV2Alpha1SpecRoutes"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SubscriptionV2Alpha1SpecRoutes.Jsii$Proxy that = (SubscriptionV2Alpha1SpecRoutes.Jsii$Proxy) o;

            if (this.defaultValue != null ? !this.defaultValue.equals(that.defaultValue) : that.defaultValue != null) return false;
            return this.rules != null ? this.rules.equals(that.rules) : that.rules == null;
        }

        @Override
        public final int hashCode() {
            int result = this.defaultValue != null ? this.defaultValue.hashCode() : 0;
            result = 31 * result + (this.rules != null ? this.rules.hashCode() : 0);
            return result;
        }
    }
}
