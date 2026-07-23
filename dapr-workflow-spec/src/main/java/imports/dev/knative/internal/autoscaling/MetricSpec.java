package imports.dev.knative.internal.autoscaling;

/**
 * Spec holds the desired state of the Metric (from the client).
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:01:58.164Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.autoscaling.$Module.class, fqn = "devknativeinternalautoscaling.MetricSpec")
@software.amazon.jsii.Jsii.Proxy(MetricSpec.Jsii$Proxy.class)
public interface MetricSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     * PanicWindow is the aggregation window for metrics where quick reactions are needed.
     */
    @org.jetbrains.annotations.NotNull java.lang.Number getPanicWindow();

    /**
     * ScrapeTarget is the K8s service that publishes the metric endpoint.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getScrapeTarget();

    /**
     * StableWindow is the aggregation window for metrics in a stable state.
     */
    @org.jetbrains.annotations.NotNull java.lang.Number getStableWindow();

    /**
     * @return a {@link Builder} of {@link MetricSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link MetricSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<MetricSpec> {
        java.lang.Number panicWindow;
        java.lang.String scrapeTarget;
        java.lang.Number stableWindow;

        /**
         * Sets the value of {@link MetricSpec#getPanicWindow}
         * @param panicWindow PanicWindow is the aggregation window for metrics where quick reactions are needed. This parameter is required.
         * @return {@code this}
         */
        public Builder panicWindow(java.lang.Number panicWindow) {
            this.panicWindow = panicWindow;
            return this;
        }

        /**
         * Sets the value of {@link MetricSpec#getScrapeTarget}
         * @param scrapeTarget ScrapeTarget is the K8s service that publishes the metric endpoint. This parameter is required.
         * @return {@code this}
         */
        public Builder scrapeTarget(java.lang.String scrapeTarget) {
            this.scrapeTarget = scrapeTarget;
            return this;
        }

        /**
         * Sets the value of {@link MetricSpec#getStableWindow}
         * @param stableWindow StableWindow is the aggregation window for metrics in a stable state. This parameter is required.
         * @return {@code this}
         */
        public Builder stableWindow(java.lang.Number stableWindow) {
            this.stableWindow = stableWindow;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link MetricSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public MetricSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link MetricSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements MetricSpec {
        private final java.lang.Number panicWindow;
        private final java.lang.String scrapeTarget;
        private final java.lang.Number stableWindow;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.panicWindow = software.amazon.jsii.Kernel.get(this, "panicWindow", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.scrapeTarget = software.amazon.jsii.Kernel.get(this, "scrapeTarget", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.stableWindow = software.amazon.jsii.Kernel.get(this, "stableWindow", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.panicWindow = java.util.Objects.requireNonNull(builder.panicWindow, "panicWindow is required");
            this.scrapeTarget = java.util.Objects.requireNonNull(builder.scrapeTarget, "scrapeTarget is required");
            this.stableWindow = java.util.Objects.requireNonNull(builder.stableWindow, "stableWindow is required");
        }

        @Override
        public final java.lang.Number getPanicWindow() {
            return this.panicWindow;
        }

        @Override
        public final java.lang.String getScrapeTarget() {
            return this.scrapeTarget;
        }

        @Override
        public final java.lang.Number getStableWindow() {
            return this.stableWindow;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("panicWindow", om.valueToTree(this.getPanicWindow()));
            data.set("scrapeTarget", om.valueToTree(this.getScrapeTarget()));
            data.set("stableWindow", om.valueToTree(this.getStableWindow()));

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeinternalautoscaling.MetricSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MetricSpec.Jsii$Proxy that = (MetricSpec.Jsii$Proxy) o;

            if (!panicWindow.equals(that.panicWindow)) return false;
            if (!scrapeTarget.equals(that.scrapeTarget)) return false;
            return this.stableWindow.equals(that.stableWindow);
        }

        @Override
        public final int hashCode() {
            int result = this.panicWindow.hashCode();
            result = 31 * result + (this.scrapeTarget.hashCode());
            result = 31 * result + (this.stableWindow.hashCode());
            return result;
        }
    }
}
