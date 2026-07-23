package imports.io.dapr;

/**
 * MCPMiddlewareHook is a single middleware hook.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.463Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.McpServerSpecMiddlewareBeforeListTools")
@software.amazon.jsii.Jsii.Proxy(McpServerSpecMiddlewareBeforeListTools.Jsii$Proxy.class)
public interface McpServerSpecMiddlewareBeforeListTools extends software.amazon.jsii.JsiiSerializable {

    /**
     * Workflow is the Dapr workflow to invoke as the hook.
     */
    @org.jetbrains.annotations.NotNull imports.io.dapr.McpServerSpecMiddlewareBeforeListToolsWorkflow getWorkflow();

    /**
     * @return a {@link Builder} of {@link McpServerSpecMiddlewareBeforeListTools}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link McpServerSpecMiddlewareBeforeListTools}
     */
    public static final class Builder implements software.amazon.jsii.Builder<McpServerSpecMiddlewareBeforeListTools> {
        imports.io.dapr.McpServerSpecMiddlewareBeforeListToolsWorkflow workflow;

        /**
         * Sets the value of {@link McpServerSpecMiddlewareBeforeListTools#getWorkflow}
         * @param workflow Workflow is the Dapr workflow to invoke as the hook. This parameter is required.
         * @return {@code this}
         */
        public Builder workflow(imports.io.dapr.McpServerSpecMiddlewareBeforeListToolsWorkflow workflow) {
            this.workflow = workflow;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link McpServerSpecMiddlewareBeforeListTools}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public McpServerSpecMiddlewareBeforeListTools build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link McpServerSpecMiddlewareBeforeListTools}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements McpServerSpecMiddlewareBeforeListTools {
        private final imports.io.dapr.McpServerSpecMiddlewareBeforeListToolsWorkflow workflow;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.workflow = software.amazon.jsii.Kernel.get(this, "workflow", software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecMiddlewareBeforeListToolsWorkflow.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.workflow = java.util.Objects.requireNonNull(builder.workflow, "workflow is required");
        }

        @Override
        public final imports.io.dapr.McpServerSpecMiddlewareBeforeListToolsWorkflow getWorkflow() {
            return this.workflow;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("workflow", om.valueToTree(this.getWorkflow()));

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.McpServerSpecMiddlewareBeforeListTools"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            McpServerSpecMiddlewareBeforeListTools.Jsii$Proxy that = (McpServerSpecMiddlewareBeforeListTools.Jsii$Proxy) o;

            return this.workflow.equals(that.workflow);
        }

        @Override
        public final int hashCode() {
            int result = this.workflow.hashCode();
            return result;
        }
    }
}
