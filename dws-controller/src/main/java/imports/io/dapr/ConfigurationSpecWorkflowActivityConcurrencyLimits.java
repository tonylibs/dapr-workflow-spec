package imports.io.dapr;

/**
 * NamedConcurrencyLimit defines a per-name concurrency limit for a specific workflow or activity name.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.450Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecWorkflowActivityConcurrencyLimits")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecWorkflowActivityConcurrencyLimits.Jsii$Proxy.class)
public interface ConfigurationSpecWorkflowActivityConcurrencyLimits extends software.amazon.jsii.JsiiSerializable {

    /**
     * MaxConcurrent is the maximum number of concurrent invocations across all replicas.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getMaxConcurrent() {
        return null;
    }

    /**
     * Name is the workflow or activity name to limit.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getName() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecWorkflowActivityConcurrencyLimits}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecWorkflowActivityConcurrencyLimits}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecWorkflowActivityConcurrencyLimits> {
        java.lang.Number maxConcurrent;
        java.lang.String name;

        /**
         * Sets the value of {@link ConfigurationSpecWorkflowActivityConcurrencyLimits#getMaxConcurrent}
         * @param maxConcurrent MaxConcurrent is the maximum number of concurrent invocations across all replicas.
         * @return {@code this}
         */
        public Builder maxConcurrent(java.lang.Number maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecWorkflowActivityConcurrencyLimits#getName}
         * @param name Name is the workflow or activity name to limit.
         * @return {@code this}
         */
        public Builder name(java.lang.String name) {
            this.name = name;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecWorkflowActivityConcurrencyLimits}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecWorkflowActivityConcurrencyLimits build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecWorkflowActivityConcurrencyLimits}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecWorkflowActivityConcurrencyLimits {
        private final java.lang.Number maxConcurrent;
        private final java.lang.String name;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.maxConcurrent = software.amazon.jsii.Kernel.get(this, "maxConcurrent", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.maxConcurrent = builder.maxConcurrent;
            this.name = builder.name;
        }

        @Override
        public final java.lang.Number getMaxConcurrent() {
            return this.maxConcurrent;
        }

        @Override
        public final java.lang.String getName() {
            return this.name;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getMaxConcurrent() != null) {
                data.set("maxConcurrent", om.valueToTree(this.getMaxConcurrent()));
            }
            if (this.getName() != null) {
                data.set("name", om.valueToTree(this.getName()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecWorkflowActivityConcurrencyLimits"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecWorkflowActivityConcurrencyLimits.Jsii$Proxy that = (ConfigurationSpecWorkflowActivityConcurrencyLimits.Jsii$Proxy) o;

            if (this.maxConcurrent != null ? !this.maxConcurrent.equals(that.maxConcurrent) : that.maxConcurrent != null) return false;
            return this.name != null ? this.name.equals(that.name) : that.name == null;
        }

        @Override
        public final int hashCode() {
            int result = this.maxConcurrent != null ? this.maxConcurrent.hashCode() : 0;
            result = 31 * result + (this.name != null ? this.name.hashCode() : 0);
            return result;
        }
    }
}
