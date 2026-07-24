package imports.io.dapr;

/**
 * MutatingMCPMiddlewareHook extends MCPMiddlewareHook with the ability to replace the data flowing through the pipeline when Mutate is true.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.463Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.McpServerSpecMiddlewareBeforeCallTool")
@software.amazon.jsii.Jsii.Proxy(McpServerSpecMiddlewareBeforeCallTool.Jsii$Proxy.class)
public interface McpServerSpecMiddlewareBeforeCallTool extends software.amazon.jsii.JsiiSerializable {

    /**
     * Workflow is the Dapr workflow to invoke as the hook.
     */
    @org.jetbrains.annotations.NotNull imports.io.dapr.McpServerSpecMiddlewareBeforeCallToolWorkflow getWorkflow();

    /**
     * Mutate, when true, causes the hook's return value to replace the data flowing through the pipeline: - beforeCallTool: replaces the arguments sent to the tool call (e.g. redact PII, inject defaults). - afterCallTool / afterListTools: replaces the result returned to the caller. When false (default), the hook validates/observes only — its output is discarded.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getMutate() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link McpServerSpecMiddlewareBeforeCallTool}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link McpServerSpecMiddlewareBeforeCallTool}
     */
    public static final class Builder implements software.amazon.jsii.Builder<McpServerSpecMiddlewareBeforeCallTool> {
        imports.io.dapr.McpServerSpecMiddlewareBeforeCallToolWorkflow workflow;
        java.lang.Boolean mutate;

        /**
         * Sets the value of {@link McpServerSpecMiddlewareBeforeCallTool#getWorkflow}
         * @param workflow Workflow is the Dapr workflow to invoke as the hook. This parameter is required.
         * @return {@code this}
         */
        public Builder workflow(imports.io.dapr.McpServerSpecMiddlewareBeforeCallToolWorkflow workflow) {
            this.workflow = workflow;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecMiddlewareBeforeCallTool#getMutate}
         * @param mutate Mutate, when true, causes the hook's return value to replace the data flowing through the pipeline: - beforeCallTool: replaces the arguments sent to the tool call (e.g. redact PII, inject defaults). - afterCallTool / afterListTools: replaces the result returned to the caller. When false (default), the hook validates/observes only — its output is discarded.
         * @return {@code this}
         */
        public Builder mutate(java.lang.Boolean mutate) {
            this.mutate = mutate;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link McpServerSpecMiddlewareBeforeCallTool}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public McpServerSpecMiddlewareBeforeCallTool build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link McpServerSpecMiddlewareBeforeCallTool}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements McpServerSpecMiddlewareBeforeCallTool {
        private final imports.io.dapr.McpServerSpecMiddlewareBeforeCallToolWorkflow workflow;
        private final java.lang.Boolean mutate;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.workflow = software.amazon.jsii.Kernel.get(this, "workflow", software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecMiddlewareBeforeCallToolWorkflow.class));
            this.mutate = software.amazon.jsii.Kernel.get(this, "mutate", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.workflow = java.util.Objects.requireNonNull(builder.workflow, "workflow is required");
            this.mutate = builder.mutate;
        }

        @Override
        public final imports.io.dapr.McpServerSpecMiddlewareBeforeCallToolWorkflow getWorkflow() {
            return this.workflow;
        }

        @Override
        public final java.lang.Boolean getMutate() {
            return this.mutate;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("workflow", om.valueToTree(this.getWorkflow()));
            if (this.getMutate() != null) {
                data.set("mutate", om.valueToTree(this.getMutate()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.McpServerSpecMiddlewareBeforeCallTool"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            McpServerSpecMiddlewareBeforeCallTool.Jsii$Proxy that = (McpServerSpecMiddlewareBeforeCallTool.Jsii$Proxy) o;

            if (!workflow.equals(that.workflow)) return false;
            return this.mutate != null ? this.mutate.equals(that.mutate) : that.mutate == null;
        }

        @Override
        public final int hashCode() {
            int result = this.workflow.hashCode();
            result = 31 * result + (this.mutate != null ? this.mutate.hashCode() : 0);
            return result;
        }
    }
}
