package imports.io.dapr;

/**
 * MetricSpec defines metrics configuration.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.447Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecMetrics")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecMetrics.Jsii$Proxy.class)
public interface ConfigurationSpecMetrics extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.Boolean getEnabled();

    /**
     * MetricHTTP defines configuration for metrics for the HTTP server.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecMetricsHttp getHttp() {
        return null;
    }

    /**
     * The LatencyDistributionBuckets variable specifies the latency distribution buckets (in milliseconds) used for histograms in the application.
     * <p>
     * If this variable is not set or left empty, the application will default to using the standard histogram buckets.
     * The default histogram latency buckets (in milliseconds) are as follows:
     * 1, 2, 3, 4, 5, 6, 8, 10, 13, 16, 20, 25, 30, 40, 50, 65, 80, 100, 130, 160, 200, 250, 300, 400, 500, 650, 800, 1,000, 2,000, 5,000, 10,000, 20,000, 50,000, 100,000.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.Number> getLatencyDistributionBuckets() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getRecordErrorCodes() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.ConfigurationSpecMetricsRules> getRules() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecMetrics}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecMetrics}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecMetrics> {
        java.lang.Boolean enabled;
        imports.io.dapr.ConfigurationSpecMetricsHttp http;
        java.util.List<java.lang.Number> latencyDistributionBuckets;
        java.lang.Boolean recordErrorCodes;
        java.util.List<imports.io.dapr.ConfigurationSpecMetricsRules> rules;

        /**
         * Sets the value of {@link ConfigurationSpecMetrics#getEnabled}
         * @param enabled the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder enabled(java.lang.Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecMetrics#getHttp}
         * @param http MetricHTTP defines configuration for metrics for the HTTP server.
         * @return {@code this}
         */
        public Builder http(imports.io.dapr.ConfigurationSpecMetricsHttp http) {
            this.http = http;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecMetrics#getLatencyDistributionBuckets}
         * @param latencyDistributionBuckets The LatencyDistributionBuckets variable specifies the latency distribution buckets (in milliseconds) used for histograms in the application.
         *                                   If this variable is not set or left empty, the application will default to using the standard histogram buckets.
         *                                   The default histogram latency buckets (in milliseconds) are as follows:
         *                                   1, 2, 3, 4, 5, 6, 8, 10, 13, 16, 20, 25, 30, 40, 50, 65, 80, 100, 130, 160, 200, 250, 300, 400, 500, 650, 800, 1,000, 2,000, 5,000, 10,000, 20,000, 50,000, 100,000.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder latencyDistributionBuckets(java.util.List<? extends java.lang.Number> latencyDistributionBuckets) {
            this.latencyDistributionBuckets = (java.util.List<java.lang.Number>)latencyDistributionBuckets;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecMetrics#getRecordErrorCodes}
         * @param recordErrorCodes the value to be set.
         * @return {@code this}
         */
        public Builder recordErrorCodes(java.lang.Boolean recordErrorCodes) {
            this.recordErrorCodes = recordErrorCodes;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecMetrics#getRules}
         * @param rules the value to be set.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder rules(java.util.List<? extends imports.io.dapr.ConfigurationSpecMetricsRules> rules) {
            this.rules = (java.util.List<imports.io.dapr.ConfigurationSpecMetricsRules>)rules;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecMetrics}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecMetrics build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecMetrics}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecMetrics {
        private final java.lang.Boolean enabled;
        private final imports.io.dapr.ConfigurationSpecMetricsHttp http;
        private final java.util.List<java.lang.Number> latencyDistributionBuckets;
        private final java.lang.Boolean recordErrorCodes;
        private final java.util.List<imports.io.dapr.ConfigurationSpecMetricsRules> rules;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.enabled = software.amazon.jsii.Kernel.get(this, "enabled", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.http = software.amazon.jsii.Kernel.get(this, "http", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecMetricsHttp.class));
            this.latencyDistributionBuckets = software.amazon.jsii.Kernel.get(this, "latencyDistributionBuckets", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.Number.class)));
            this.recordErrorCodes = software.amazon.jsii.Kernel.get(this, "recordErrorCodes", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.rules = software.amazon.jsii.Kernel.get(this, "rules", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecMetricsRules.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.enabled = java.util.Objects.requireNonNull(builder.enabled, "enabled is required");
            this.http = builder.http;
            this.latencyDistributionBuckets = (java.util.List<java.lang.Number>)builder.latencyDistributionBuckets;
            this.recordErrorCodes = builder.recordErrorCodes;
            this.rules = (java.util.List<imports.io.dapr.ConfigurationSpecMetricsRules>)builder.rules;
        }

        @Override
        public final java.lang.Boolean getEnabled() {
            return this.enabled;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecMetricsHttp getHttp() {
            return this.http;
        }

        @Override
        public final java.util.List<java.lang.Number> getLatencyDistributionBuckets() {
            return this.latencyDistributionBuckets;
        }

        @Override
        public final java.lang.Boolean getRecordErrorCodes() {
            return this.recordErrorCodes;
        }

        @Override
        public final java.util.List<imports.io.dapr.ConfigurationSpecMetricsRules> getRules() {
            return this.rules;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("enabled", om.valueToTree(this.getEnabled()));
            if (this.getHttp() != null) {
                data.set("http", om.valueToTree(this.getHttp()));
            }
            if (this.getLatencyDistributionBuckets() != null) {
                data.set("latencyDistributionBuckets", om.valueToTree(this.getLatencyDistributionBuckets()));
            }
            if (this.getRecordErrorCodes() != null) {
                data.set("recordErrorCodes", om.valueToTree(this.getRecordErrorCodes()));
            }
            if (this.getRules() != null) {
                data.set("rules", om.valueToTree(this.getRules()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecMetrics"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecMetrics.Jsii$Proxy that = (ConfigurationSpecMetrics.Jsii$Proxy) o;

            if (!enabled.equals(that.enabled)) return false;
            if (this.http != null ? !this.http.equals(that.http) : that.http != null) return false;
            if (this.latencyDistributionBuckets != null ? !this.latencyDistributionBuckets.equals(that.latencyDistributionBuckets) : that.latencyDistributionBuckets != null) return false;
            if (this.recordErrorCodes != null ? !this.recordErrorCodes.equals(that.recordErrorCodes) : that.recordErrorCodes != null) return false;
            return this.rules != null ? this.rules.equals(that.rules) : that.rules == null;
        }

        @Override
        public final int hashCode() {
            int result = this.enabled.hashCode();
            result = 31 * result + (this.http != null ? this.http.hashCode() : 0);
            result = 31 * result + (this.latencyDistributionBuckets != null ? this.latencyDistributionBuckets.hashCode() : 0);
            result = 31 * result + (this.recordErrorCodes != null ? this.recordErrorCodes.hashCode() : 0);
            result = 31 * result + (this.rules != null ? this.rules.hashCode() : 0);
            return result;
        }
    }
}
