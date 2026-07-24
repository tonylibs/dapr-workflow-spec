package imports.io.dapr;

/**
 * TracingSpec defines distributed tracing configuration.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.449Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecTracing")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecTracing.Jsii$Proxy.class)
public interface ConfigurationSpecTracing extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getSamplingRate();

    /**
     * OtelSpec defines Otel exporter configurations.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecTracingOtel getOtel() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getStdout() {
        return null;
    }

    /**
     * ZipkinSpec defines Zipkin trace configurations.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecTracingZipkin getZipkin() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecTracing}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecTracing}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecTracing> {
        java.lang.String samplingRate;
        imports.io.dapr.ConfigurationSpecTracingOtel otel;
        java.lang.Boolean stdout;
        imports.io.dapr.ConfigurationSpecTracingZipkin zipkin;

        /**
         * Sets the value of {@link ConfigurationSpecTracing#getSamplingRate}
         * @param samplingRate the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder samplingRate(java.lang.String samplingRate) {
            this.samplingRate = samplingRate;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTracing#getOtel}
         * @param otel OtelSpec defines Otel exporter configurations.
         * @return {@code this}
         */
        public Builder otel(imports.io.dapr.ConfigurationSpecTracingOtel otel) {
            this.otel = otel;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTracing#getStdout}
         * @param stdout the value to be set.
         * @return {@code this}
         */
        public Builder stdout(java.lang.Boolean stdout) {
            this.stdout = stdout;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTracing#getZipkin}
         * @param zipkin ZipkinSpec defines Zipkin trace configurations.
         * @return {@code this}
         */
        public Builder zipkin(imports.io.dapr.ConfigurationSpecTracingZipkin zipkin) {
            this.zipkin = zipkin;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecTracing}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecTracing build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecTracing}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecTracing {
        private final java.lang.String samplingRate;
        private final imports.io.dapr.ConfigurationSpecTracingOtel otel;
        private final java.lang.Boolean stdout;
        private final imports.io.dapr.ConfigurationSpecTracingZipkin zipkin;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.samplingRate = software.amazon.jsii.Kernel.get(this, "samplingRate", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.otel = software.amazon.jsii.Kernel.get(this, "otel", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecTracingOtel.class));
            this.stdout = software.amazon.jsii.Kernel.get(this, "stdout", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.zipkin = software.amazon.jsii.Kernel.get(this, "zipkin", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecTracingZipkin.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.samplingRate = java.util.Objects.requireNonNull(builder.samplingRate, "samplingRate is required");
            this.otel = builder.otel;
            this.stdout = builder.stdout;
            this.zipkin = builder.zipkin;
        }

        @Override
        public final java.lang.String getSamplingRate() {
            return this.samplingRate;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecTracingOtel getOtel() {
            return this.otel;
        }

        @Override
        public final java.lang.Boolean getStdout() {
            return this.stdout;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecTracingZipkin getZipkin() {
            return this.zipkin;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("samplingRate", om.valueToTree(this.getSamplingRate()));
            if (this.getOtel() != null) {
                data.set("otel", om.valueToTree(this.getOtel()));
            }
            if (this.getStdout() != null) {
                data.set("stdout", om.valueToTree(this.getStdout()));
            }
            if (this.getZipkin() != null) {
                data.set("zipkin", om.valueToTree(this.getZipkin()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecTracing"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecTracing.Jsii$Proxy that = (ConfigurationSpecTracing.Jsii$Proxy) o;

            if (!samplingRate.equals(that.samplingRate)) return false;
            if (this.otel != null ? !this.otel.equals(that.otel) : that.otel != null) return false;
            if (this.stdout != null ? !this.stdout.equals(that.stdout) : that.stdout != null) return false;
            return this.zipkin != null ? this.zipkin.equals(that.zipkin) : that.zipkin == null;
        }

        @Override
        public final int hashCode() {
            int result = this.samplingRate.hashCode();
            result = 31 * result + (this.otel != null ? this.otel.hashCode() : 0);
            result = 31 * result + (this.stdout != null ? this.stdout.hashCode() : 0);
            result = 31 * result + (this.zipkin != null ? this.zipkin.hashCode() : 0);
            return result;
        }
    }
}
