package imports.io.dapr;

/**
 * MetricHTTP defines configuration for metrics for the HTTP server.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.447Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecMetricsHttp")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecMetricsHttp.Jsii$Proxy.class)
public interface ConfigurationSpecMetricsHttp extends software.amazon.jsii.JsiiSerializable {

    /**
     * If true (default is false) HTTP verbs (e.g., GET, POST) are excluded from the metrics.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getExcludeVerbs() {
        return null;
    }

    /**
     * If false, metrics for the HTTP server are collected with increased cardinality.
     * <p>
     * The default is true in Dapr 1.13, but will be changed to false in 1.15+
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getIncreasedCardinality() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getPathMatching() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecMetricsHttp}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecMetricsHttp}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecMetricsHttp> {
        java.lang.Boolean excludeVerbs;
        java.lang.Boolean increasedCardinality;
        java.util.List<java.lang.String> pathMatching;

        /**
         * Sets the value of {@link ConfigurationSpecMetricsHttp#getExcludeVerbs}
         * @param excludeVerbs If true (default is false) HTTP verbs (e.g., GET, POST) are excluded from the metrics.
         * @return {@code this}
         */
        public Builder excludeVerbs(java.lang.Boolean excludeVerbs) {
            this.excludeVerbs = excludeVerbs;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecMetricsHttp#getIncreasedCardinality}
         * @param increasedCardinality If false, metrics for the HTTP server are collected with increased cardinality.
         *                             The default is true in Dapr 1.13, but will be changed to false in 1.15+
         * @return {@code this}
         */
        public Builder increasedCardinality(java.lang.Boolean increasedCardinality) {
            this.increasedCardinality = increasedCardinality;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecMetricsHttp#getPathMatching}
         * @param pathMatching the value to be set.
         * @return {@code this}
         */
        public Builder pathMatching(java.util.List<java.lang.String> pathMatching) {
            this.pathMatching = pathMatching;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecMetricsHttp}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecMetricsHttp build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecMetricsHttp}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecMetricsHttp {
        private final java.lang.Boolean excludeVerbs;
        private final java.lang.Boolean increasedCardinality;
        private final java.util.List<java.lang.String> pathMatching;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.excludeVerbs = software.amazon.jsii.Kernel.get(this, "excludeVerbs", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.increasedCardinality = software.amazon.jsii.Kernel.get(this, "increasedCardinality", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.pathMatching = software.amazon.jsii.Kernel.get(this, "pathMatching", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.excludeVerbs = builder.excludeVerbs;
            this.increasedCardinality = builder.increasedCardinality;
            this.pathMatching = builder.pathMatching;
        }

        @Override
        public final java.lang.Boolean getExcludeVerbs() {
            return this.excludeVerbs;
        }

        @Override
        public final java.lang.Boolean getIncreasedCardinality() {
            return this.increasedCardinality;
        }

        @Override
        public final java.util.List<java.lang.String> getPathMatching() {
            return this.pathMatching;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getExcludeVerbs() != null) {
                data.set("excludeVerbs", om.valueToTree(this.getExcludeVerbs()));
            }
            if (this.getIncreasedCardinality() != null) {
                data.set("increasedCardinality", om.valueToTree(this.getIncreasedCardinality()));
            }
            if (this.getPathMatching() != null) {
                data.set("pathMatching", om.valueToTree(this.getPathMatching()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecMetricsHttp"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecMetricsHttp.Jsii$Proxy that = (ConfigurationSpecMetricsHttp.Jsii$Proxy) o;

            if (this.excludeVerbs != null ? !this.excludeVerbs.equals(that.excludeVerbs) : that.excludeVerbs != null) return false;
            if (this.increasedCardinality != null ? !this.increasedCardinality.equals(that.increasedCardinality) : that.increasedCardinality != null) return false;
            return this.pathMatching != null ? this.pathMatching.equals(that.pathMatching) : that.pathMatching == null;
        }

        @Override
        public final int hashCode() {
            int result = this.excludeVerbs != null ? this.excludeVerbs.hashCode() : 0;
            result = 31 * result + (this.increasedCardinality != null ? this.increasedCardinality.hashCode() : 0);
            result = 31 * result + (this.pathMatching != null ? this.pathMatching.hashCode() : 0);
            return result;
        }
    }
}
