package imports.io.dapr;

/**
 * Workflow is the Dapr workflow to invoke as the hook.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.463Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.McpServerSpecMiddlewareAfterListToolsWorkflow")
@software.amazon.jsii.Jsii.Proxy(McpServerSpecMiddlewareAfterListToolsWorkflow.Jsii$Proxy.class)
public interface McpServerSpecMiddlewareAfterListToolsWorkflow extends software.amazon.jsii.JsiiSerializable {

    /**
     * WorkflowName is the name of the workflow to invoke.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getWorkflowName();

    /**
     * AppID targets the workflow on a remote Dapr app via service invocation.
     * <p>
     * When unset, the workflow is invoked locally.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getAppId() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link McpServerSpecMiddlewareAfterListToolsWorkflow}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link McpServerSpecMiddlewareAfterListToolsWorkflow}
     */
    public static final class Builder implements software.amazon.jsii.Builder<McpServerSpecMiddlewareAfterListToolsWorkflow> {
        java.lang.String workflowName;
        java.lang.String appId;

        /**
         * Sets the value of {@link McpServerSpecMiddlewareAfterListToolsWorkflow#getWorkflowName}
         * @param workflowName WorkflowName is the name of the workflow to invoke. This parameter is required.
         * @return {@code this}
         */
        public Builder workflowName(java.lang.String workflowName) {
            this.workflowName = workflowName;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecMiddlewareAfterListToolsWorkflow#getAppId}
         * @param appId AppID targets the workflow on a remote Dapr app via service invocation.
         *              When unset, the workflow is invoked locally.
         * @return {@code this}
         */
        public Builder appId(java.lang.String appId) {
            this.appId = appId;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link McpServerSpecMiddlewareAfterListToolsWorkflow}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public McpServerSpecMiddlewareAfterListToolsWorkflow build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link McpServerSpecMiddlewareAfterListToolsWorkflow}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements McpServerSpecMiddlewareAfterListToolsWorkflow {
        private final java.lang.String workflowName;
        private final java.lang.String appId;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.workflowName = software.amazon.jsii.Kernel.get(this, "workflowName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.appId = software.amazon.jsii.Kernel.get(this, "appId", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.workflowName = java.util.Objects.requireNonNull(builder.workflowName, "workflowName is required");
            this.appId = builder.appId;
        }

        @Override
        public final java.lang.String getWorkflowName() {
            return this.workflowName;
        }

        @Override
        public final java.lang.String getAppId() {
            return this.appId;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("workflowName", om.valueToTree(this.getWorkflowName()));
            if (this.getAppId() != null) {
                data.set("appId", om.valueToTree(this.getAppId()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.McpServerSpecMiddlewareAfterListToolsWorkflow"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            McpServerSpecMiddlewareAfterListToolsWorkflow.Jsii$Proxy that = (McpServerSpecMiddlewareAfterListToolsWorkflow.Jsii$Proxy) o;

            if (!workflowName.equals(that.workflowName)) return false;
            return this.appId != null ? this.appId.equals(that.appId) : that.appId == null;
        }

        @Override
        public final int hashCode() {
            int result = this.workflowName.hashCode();
            result = 31 * result + (this.appId != null ? this.appId.hashCode() : 0);
            return result;
        }
    }
}
