package imports.io.dapr;

/**
 * ConfigurationSpec is the spec for a configuration.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.437Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpec")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpec.Jsii$Proxy.class)
public interface ConfigurationSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     * AccessControlSpec is the spec object in ConfigurationSpec.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecAccessControl getAccessControl() {
        return null;
    }

    /**
     * APISpec describes the configuration for Dapr APIs.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecApi getApi() {
        return null;
    }

    /**
     * PipelineSpec defines the middleware pipeline.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecAppHttpPipeline getAppHttpPipeline() {
        return null;
    }

    /**
     * ComponentsSpec describes the configuration for Dapr components.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecComponents getComponents() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.ConfigurationSpecFeatures> getFeatures() {
        return null;
    }

    /**
     * PipelineSpec defines the middleware pipeline.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecHttpPipeline getHttpPipeline() {
        return null;
    }

    /**
     * LoggingSpec defines the configuration for logging.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecLogging getLogging() {
        return null;
    }

    /**
     * MetricSpec defines metrics configuration.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecMetric getMetric() {
        return null;
    }

    /**
     * MetricSpec defines metrics configuration.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecMetrics getMetrics() {
        return null;
    }

    /**
     * MTLSSpec defines mTLS configuration.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecMtls getMtls() {
        return null;
    }

    /**
     * NameResolutionSpec is the spec for name resolution configuration.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecNameResolution getNameResolution() {
        return null;
    }

    /**
     * SecretsSpec is the spec for secrets configuration.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecSecrets getSecrets() {
        return null;
    }

    /**
     * TracingSpec defines distributed tracing configuration.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecTracing getTracing() {
        return null;
    }

    /**
     * WasmSpec describes the security profile for all Dapr Wasm components.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecWasm getWasm() {
        return null;
    }

    /**
     * WorkflowSpec defines the configuration for Dapr workflows.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecWorkflow getWorkflow() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpec> {
        imports.io.dapr.ConfigurationSpecAccessControl accessControl;
        imports.io.dapr.ConfigurationSpecApi api;
        imports.io.dapr.ConfigurationSpecAppHttpPipeline appHttpPipeline;
        imports.io.dapr.ConfigurationSpecComponents components;
        java.util.List<imports.io.dapr.ConfigurationSpecFeatures> features;
        imports.io.dapr.ConfigurationSpecHttpPipeline httpPipeline;
        imports.io.dapr.ConfigurationSpecLogging logging;
        imports.io.dapr.ConfigurationSpecMetric metric;
        imports.io.dapr.ConfigurationSpecMetrics metrics;
        imports.io.dapr.ConfigurationSpecMtls mtls;
        imports.io.dapr.ConfigurationSpecNameResolution nameResolution;
        imports.io.dapr.ConfigurationSpecSecrets secrets;
        imports.io.dapr.ConfigurationSpecTracing tracing;
        imports.io.dapr.ConfigurationSpecWasm wasm;
        imports.io.dapr.ConfigurationSpecWorkflow workflow;

        /**
         * Sets the value of {@link ConfigurationSpec#getAccessControl}
         * @param accessControl AccessControlSpec is the spec object in ConfigurationSpec.
         * @return {@code this}
         */
        public Builder accessControl(imports.io.dapr.ConfigurationSpecAccessControl accessControl) {
            this.accessControl = accessControl;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpec#getApi}
         * @param api APISpec describes the configuration for Dapr APIs.
         * @return {@code this}
         */
        public Builder api(imports.io.dapr.ConfigurationSpecApi api) {
            this.api = api;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpec#getAppHttpPipeline}
         * @param appHttpPipeline PipelineSpec defines the middleware pipeline.
         * @return {@code this}
         */
        public Builder appHttpPipeline(imports.io.dapr.ConfigurationSpecAppHttpPipeline appHttpPipeline) {
            this.appHttpPipeline = appHttpPipeline;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpec#getComponents}
         * @param components ComponentsSpec describes the configuration for Dapr components.
         * @return {@code this}
         */
        public Builder components(imports.io.dapr.ConfigurationSpecComponents components) {
            this.components = components;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpec#getFeatures}
         * @param features the value to be set.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder features(java.util.List<? extends imports.io.dapr.ConfigurationSpecFeatures> features) {
            this.features = (java.util.List<imports.io.dapr.ConfigurationSpecFeatures>)features;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpec#getHttpPipeline}
         * @param httpPipeline PipelineSpec defines the middleware pipeline.
         * @return {@code this}
         */
        public Builder httpPipeline(imports.io.dapr.ConfigurationSpecHttpPipeline httpPipeline) {
            this.httpPipeline = httpPipeline;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpec#getLogging}
         * @param logging LoggingSpec defines the configuration for logging.
         * @return {@code this}
         */
        public Builder logging(imports.io.dapr.ConfigurationSpecLogging logging) {
            this.logging = logging;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpec#getMetric}
         * @param metric MetricSpec defines metrics configuration.
         * @return {@code this}
         */
        public Builder metric(imports.io.dapr.ConfigurationSpecMetric metric) {
            this.metric = metric;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpec#getMetrics}
         * @param metrics MetricSpec defines metrics configuration.
         * @return {@code this}
         */
        public Builder metrics(imports.io.dapr.ConfigurationSpecMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpec#getMtls}
         * @param mtls MTLSSpec defines mTLS configuration.
         * @return {@code this}
         */
        public Builder mtls(imports.io.dapr.ConfigurationSpecMtls mtls) {
            this.mtls = mtls;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpec#getNameResolution}
         * @param nameResolution NameResolutionSpec is the spec for name resolution configuration.
         * @return {@code this}
         */
        public Builder nameResolution(imports.io.dapr.ConfigurationSpecNameResolution nameResolution) {
            this.nameResolution = nameResolution;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpec#getSecrets}
         * @param secrets SecretsSpec is the spec for secrets configuration.
         * @return {@code this}
         */
        public Builder secrets(imports.io.dapr.ConfigurationSpecSecrets secrets) {
            this.secrets = secrets;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpec#getTracing}
         * @param tracing TracingSpec defines distributed tracing configuration.
         * @return {@code this}
         */
        public Builder tracing(imports.io.dapr.ConfigurationSpecTracing tracing) {
            this.tracing = tracing;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpec#getWasm}
         * @param wasm WasmSpec describes the security profile for all Dapr Wasm components.
         * @return {@code this}
         */
        public Builder wasm(imports.io.dapr.ConfigurationSpecWasm wasm) {
            this.wasm = wasm;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpec#getWorkflow}
         * @param workflow WorkflowSpec defines the configuration for Dapr workflows.
         * @return {@code this}
         */
        public Builder workflow(imports.io.dapr.ConfigurationSpecWorkflow workflow) {
            this.workflow = workflow;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpec {
        private final imports.io.dapr.ConfigurationSpecAccessControl accessControl;
        private final imports.io.dapr.ConfigurationSpecApi api;
        private final imports.io.dapr.ConfigurationSpecAppHttpPipeline appHttpPipeline;
        private final imports.io.dapr.ConfigurationSpecComponents components;
        private final java.util.List<imports.io.dapr.ConfigurationSpecFeatures> features;
        private final imports.io.dapr.ConfigurationSpecHttpPipeline httpPipeline;
        private final imports.io.dapr.ConfigurationSpecLogging logging;
        private final imports.io.dapr.ConfigurationSpecMetric metric;
        private final imports.io.dapr.ConfigurationSpecMetrics metrics;
        private final imports.io.dapr.ConfigurationSpecMtls mtls;
        private final imports.io.dapr.ConfigurationSpecNameResolution nameResolution;
        private final imports.io.dapr.ConfigurationSpecSecrets secrets;
        private final imports.io.dapr.ConfigurationSpecTracing tracing;
        private final imports.io.dapr.ConfigurationSpecWasm wasm;
        private final imports.io.dapr.ConfigurationSpecWorkflow workflow;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.accessControl = software.amazon.jsii.Kernel.get(this, "accessControl", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecAccessControl.class));
            this.api = software.amazon.jsii.Kernel.get(this, "api", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecApi.class));
            this.appHttpPipeline = software.amazon.jsii.Kernel.get(this, "appHttpPipeline", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecAppHttpPipeline.class));
            this.components = software.amazon.jsii.Kernel.get(this, "components", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecComponents.class));
            this.features = software.amazon.jsii.Kernel.get(this, "features", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecFeatures.class)));
            this.httpPipeline = software.amazon.jsii.Kernel.get(this, "httpPipeline", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecHttpPipeline.class));
            this.logging = software.amazon.jsii.Kernel.get(this, "logging", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecLogging.class));
            this.metric = software.amazon.jsii.Kernel.get(this, "metric", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecMetric.class));
            this.metrics = software.amazon.jsii.Kernel.get(this, "metrics", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecMetrics.class));
            this.mtls = software.amazon.jsii.Kernel.get(this, "mtls", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecMtls.class));
            this.nameResolution = software.amazon.jsii.Kernel.get(this, "nameResolution", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecNameResolution.class));
            this.secrets = software.amazon.jsii.Kernel.get(this, "secrets", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecSecrets.class));
            this.tracing = software.amazon.jsii.Kernel.get(this, "tracing", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecTracing.class));
            this.wasm = software.amazon.jsii.Kernel.get(this, "wasm", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecWasm.class));
            this.workflow = software.amazon.jsii.Kernel.get(this, "workflow", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecWorkflow.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.accessControl = builder.accessControl;
            this.api = builder.api;
            this.appHttpPipeline = builder.appHttpPipeline;
            this.components = builder.components;
            this.features = (java.util.List<imports.io.dapr.ConfigurationSpecFeatures>)builder.features;
            this.httpPipeline = builder.httpPipeline;
            this.logging = builder.logging;
            this.metric = builder.metric;
            this.metrics = builder.metrics;
            this.mtls = builder.mtls;
            this.nameResolution = builder.nameResolution;
            this.secrets = builder.secrets;
            this.tracing = builder.tracing;
            this.wasm = builder.wasm;
            this.workflow = builder.workflow;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecAccessControl getAccessControl() {
            return this.accessControl;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecApi getApi() {
            return this.api;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecAppHttpPipeline getAppHttpPipeline() {
            return this.appHttpPipeline;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecComponents getComponents() {
            return this.components;
        }

        @Override
        public final java.util.List<imports.io.dapr.ConfigurationSpecFeatures> getFeatures() {
            return this.features;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecHttpPipeline getHttpPipeline() {
            return this.httpPipeline;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecLogging getLogging() {
            return this.logging;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecMetric getMetric() {
            return this.metric;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecMetrics getMetrics() {
            return this.metrics;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecMtls getMtls() {
            return this.mtls;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecNameResolution getNameResolution() {
            return this.nameResolution;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecSecrets getSecrets() {
            return this.secrets;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecTracing getTracing() {
            return this.tracing;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecWasm getWasm() {
            return this.wasm;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecWorkflow getWorkflow() {
            return this.workflow;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getAccessControl() != null) {
                data.set("accessControl", om.valueToTree(this.getAccessControl()));
            }
            if (this.getApi() != null) {
                data.set("api", om.valueToTree(this.getApi()));
            }
            if (this.getAppHttpPipeline() != null) {
                data.set("appHttpPipeline", om.valueToTree(this.getAppHttpPipeline()));
            }
            if (this.getComponents() != null) {
                data.set("components", om.valueToTree(this.getComponents()));
            }
            if (this.getFeatures() != null) {
                data.set("features", om.valueToTree(this.getFeatures()));
            }
            if (this.getHttpPipeline() != null) {
                data.set("httpPipeline", om.valueToTree(this.getHttpPipeline()));
            }
            if (this.getLogging() != null) {
                data.set("logging", om.valueToTree(this.getLogging()));
            }
            if (this.getMetric() != null) {
                data.set("metric", om.valueToTree(this.getMetric()));
            }
            if (this.getMetrics() != null) {
                data.set("metrics", om.valueToTree(this.getMetrics()));
            }
            if (this.getMtls() != null) {
                data.set("mtls", om.valueToTree(this.getMtls()));
            }
            if (this.getNameResolution() != null) {
                data.set("nameResolution", om.valueToTree(this.getNameResolution()));
            }
            if (this.getSecrets() != null) {
                data.set("secrets", om.valueToTree(this.getSecrets()));
            }
            if (this.getTracing() != null) {
                data.set("tracing", om.valueToTree(this.getTracing()));
            }
            if (this.getWasm() != null) {
                data.set("wasm", om.valueToTree(this.getWasm()));
            }
            if (this.getWorkflow() != null) {
                data.set("workflow", om.valueToTree(this.getWorkflow()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpec.Jsii$Proxy that = (ConfigurationSpec.Jsii$Proxy) o;

            if (this.accessControl != null ? !this.accessControl.equals(that.accessControl) : that.accessControl != null) return false;
            if (this.api != null ? !this.api.equals(that.api) : that.api != null) return false;
            if (this.appHttpPipeline != null ? !this.appHttpPipeline.equals(that.appHttpPipeline) : that.appHttpPipeline != null) return false;
            if (this.components != null ? !this.components.equals(that.components) : that.components != null) return false;
            if (this.features != null ? !this.features.equals(that.features) : that.features != null) return false;
            if (this.httpPipeline != null ? !this.httpPipeline.equals(that.httpPipeline) : that.httpPipeline != null) return false;
            if (this.logging != null ? !this.logging.equals(that.logging) : that.logging != null) return false;
            if (this.metric != null ? !this.metric.equals(that.metric) : that.metric != null) return false;
            if (this.metrics != null ? !this.metrics.equals(that.metrics) : that.metrics != null) return false;
            if (this.mtls != null ? !this.mtls.equals(that.mtls) : that.mtls != null) return false;
            if (this.nameResolution != null ? !this.nameResolution.equals(that.nameResolution) : that.nameResolution != null) return false;
            if (this.secrets != null ? !this.secrets.equals(that.secrets) : that.secrets != null) return false;
            if (this.tracing != null ? !this.tracing.equals(that.tracing) : that.tracing != null) return false;
            if (this.wasm != null ? !this.wasm.equals(that.wasm) : that.wasm != null) return false;
            return this.workflow != null ? this.workflow.equals(that.workflow) : that.workflow == null;
        }

        @Override
        public final int hashCode() {
            int result = this.accessControl != null ? this.accessControl.hashCode() : 0;
            result = 31 * result + (this.api != null ? this.api.hashCode() : 0);
            result = 31 * result + (this.appHttpPipeline != null ? this.appHttpPipeline.hashCode() : 0);
            result = 31 * result + (this.components != null ? this.components.hashCode() : 0);
            result = 31 * result + (this.features != null ? this.features.hashCode() : 0);
            result = 31 * result + (this.httpPipeline != null ? this.httpPipeline.hashCode() : 0);
            result = 31 * result + (this.logging != null ? this.logging.hashCode() : 0);
            result = 31 * result + (this.metric != null ? this.metric.hashCode() : 0);
            result = 31 * result + (this.metrics != null ? this.metrics.hashCode() : 0);
            result = 31 * result + (this.mtls != null ? this.mtls.hashCode() : 0);
            result = 31 * result + (this.nameResolution != null ? this.nameResolution.hashCode() : 0);
            result = 31 * result + (this.secrets != null ? this.secrets.hashCode() : 0);
            result = 31 * result + (this.tracing != null ? this.tracing.hashCode() : 0);
            result = 31 * result + (this.wasm != null ? this.wasm.hashCode() : 0);
            result = 31 * result + (this.workflow != null ? this.workflow.hashCode() : 0);
            return result;
        }
    }
}
