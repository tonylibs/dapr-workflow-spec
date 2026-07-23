package imports.io.dapr;

/**
 * Middleware defines optional workflow hooks invoked around each tool call.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.462Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.McpServerSpecMiddleware")
@software.amazon.jsii.Jsii.Proxy(McpServerSpecMiddleware.Jsii$Proxy.class)
public interface McpServerSpecMiddleware extends software.amazon.jsii.JsiiSerializable {

    /**
     * AfterCallTool hooks are invoked in order after each CallTool.
     * <p>
     * Receives {mcpServer, toolName, arguments, result} as input.
     * Errors fail the workflow — after-hooks act as authorization gates.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.McpServerSpecMiddlewareAfterCallTool> getAfterCallTool() {
        return null;
    }

    /**
     * AfterListTools hooks are invoked in order after each ListTools.
     * <p>
     * Receives {mcpServer, result} as input.
     * Errors are logged but do not affect the result.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.McpServerSpecMiddlewareAfterListTools> getAfterListTools() {
        return null;
    }

    /**
     * BeforeCallTool hooks are invoked in order before each CallTool.
     * <p>
     * Receives {mcpServer, toolName, arguments} as input.
     * If any hook returns an error, the chain stops and the workflow completes
     * with CallToolResult{isError: true} so the agent/LLM can self-correct.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.McpServerSpecMiddlewareBeforeCallTool> getBeforeCallTool() {
        return null;
    }

    /**
     * BeforeListTools hooks are invoked in order before each ListTools.
     * <p>
     * Receives {mcpServer} as input.
     * If any hook returns an error, the chain stops and the error is returned.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.McpServerSpecMiddlewareBeforeListTools> getBeforeListTools() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link McpServerSpecMiddleware}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link McpServerSpecMiddleware}
     */
    public static final class Builder implements software.amazon.jsii.Builder<McpServerSpecMiddleware> {
        java.util.List<imports.io.dapr.McpServerSpecMiddlewareAfterCallTool> afterCallTool;
        java.util.List<imports.io.dapr.McpServerSpecMiddlewareAfterListTools> afterListTools;
        java.util.List<imports.io.dapr.McpServerSpecMiddlewareBeforeCallTool> beforeCallTool;
        java.util.List<imports.io.dapr.McpServerSpecMiddlewareBeforeListTools> beforeListTools;

        /**
         * Sets the value of {@link McpServerSpecMiddleware#getAfterCallTool}
         * @param afterCallTool AfterCallTool hooks are invoked in order after each CallTool.
         *                      Receives {mcpServer, toolName, arguments, result} as input.
         *                      Errors fail the workflow — after-hooks act as authorization gates.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder afterCallTool(java.util.List<? extends imports.io.dapr.McpServerSpecMiddlewareAfterCallTool> afterCallTool) {
            this.afterCallTool = (java.util.List<imports.io.dapr.McpServerSpecMiddlewareAfterCallTool>)afterCallTool;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecMiddleware#getAfterListTools}
         * @param afterListTools AfterListTools hooks are invoked in order after each ListTools.
         *                       Receives {mcpServer, result} as input.
         *                       Errors are logged but do not affect the result.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder afterListTools(java.util.List<? extends imports.io.dapr.McpServerSpecMiddlewareAfterListTools> afterListTools) {
            this.afterListTools = (java.util.List<imports.io.dapr.McpServerSpecMiddlewareAfterListTools>)afterListTools;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecMiddleware#getBeforeCallTool}
         * @param beforeCallTool BeforeCallTool hooks are invoked in order before each CallTool.
         *                       Receives {mcpServer, toolName, arguments} as input.
         *                       If any hook returns an error, the chain stops and the workflow completes
         *                       with CallToolResult{isError: true} so the agent/LLM can self-correct.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder beforeCallTool(java.util.List<? extends imports.io.dapr.McpServerSpecMiddlewareBeforeCallTool> beforeCallTool) {
            this.beforeCallTool = (java.util.List<imports.io.dapr.McpServerSpecMiddlewareBeforeCallTool>)beforeCallTool;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecMiddleware#getBeforeListTools}
         * @param beforeListTools BeforeListTools hooks are invoked in order before each ListTools.
         *                        Receives {mcpServer} as input.
         *                        If any hook returns an error, the chain stops and the error is returned.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder beforeListTools(java.util.List<? extends imports.io.dapr.McpServerSpecMiddlewareBeforeListTools> beforeListTools) {
            this.beforeListTools = (java.util.List<imports.io.dapr.McpServerSpecMiddlewareBeforeListTools>)beforeListTools;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link McpServerSpecMiddleware}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public McpServerSpecMiddleware build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link McpServerSpecMiddleware}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements McpServerSpecMiddleware {
        private final java.util.List<imports.io.dapr.McpServerSpecMiddlewareAfterCallTool> afterCallTool;
        private final java.util.List<imports.io.dapr.McpServerSpecMiddlewareAfterListTools> afterListTools;
        private final java.util.List<imports.io.dapr.McpServerSpecMiddlewareBeforeCallTool> beforeCallTool;
        private final java.util.List<imports.io.dapr.McpServerSpecMiddlewareBeforeListTools> beforeListTools;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.afterCallTool = software.amazon.jsii.Kernel.get(this, "afterCallTool", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecMiddlewareAfterCallTool.class)));
            this.afterListTools = software.amazon.jsii.Kernel.get(this, "afterListTools", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecMiddlewareAfterListTools.class)));
            this.beforeCallTool = software.amazon.jsii.Kernel.get(this, "beforeCallTool", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecMiddlewareBeforeCallTool.class)));
            this.beforeListTools = software.amazon.jsii.Kernel.get(this, "beforeListTools", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecMiddlewareBeforeListTools.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.afterCallTool = (java.util.List<imports.io.dapr.McpServerSpecMiddlewareAfterCallTool>)builder.afterCallTool;
            this.afterListTools = (java.util.List<imports.io.dapr.McpServerSpecMiddlewareAfterListTools>)builder.afterListTools;
            this.beforeCallTool = (java.util.List<imports.io.dapr.McpServerSpecMiddlewareBeforeCallTool>)builder.beforeCallTool;
            this.beforeListTools = (java.util.List<imports.io.dapr.McpServerSpecMiddlewareBeforeListTools>)builder.beforeListTools;
        }

        @Override
        public final java.util.List<imports.io.dapr.McpServerSpecMiddlewareAfterCallTool> getAfterCallTool() {
            return this.afterCallTool;
        }

        @Override
        public final java.util.List<imports.io.dapr.McpServerSpecMiddlewareAfterListTools> getAfterListTools() {
            return this.afterListTools;
        }

        @Override
        public final java.util.List<imports.io.dapr.McpServerSpecMiddlewareBeforeCallTool> getBeforeCallTool() {
            return this.beforeCallTool;
        }

        @Override
        public final java.util.List<imports.io.dapr.McpServerSpecMiddlewareBeforeListTools> getBeforeListTools() {
            return this.beforeListTools;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getAfterCallTool() != null) {
                data.set("afterCallTool", om.valueToTree(this.getAfterCallTool()));
            }
            if (this.getAfterListTools() != null) {
                data.set("afterListTools", om.valueToTree(this.getAfterListTools()));
            }
            if (this.getBeforeCallTool() != null) {
                data.set("beforeCallTool", om.valueToTree(this.getBeforeCallTool()));
            }
            if (this.getBeforeListTools() != null) {
                data.set("beforeListTools", om.valueToTree(this.getBeforeListTools()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.McpServerSpecMiddleware"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            McpServerSpecMiddleware.Jsii$Proxy that = (McpServerSpecMiddleware.Jsii$Proxy) o;

            if (this.afterCallTool != null ? !this.afterCallTool.equals(that.afterCallTool) : that.afterCallTool != null) return false;
            if (this.afterListTools != null ? !this.afterListTools.equals(that.afterListTools) : that.afterListTools != null) return false;
            if (this.beforeCallTool != null ? !this.beforeCallTool.equals(that.beforeCallTool) : that.beforeCallTool != null) return false;
            return this.beforeListTools != null ? this.beforeListTools.equals(that.beforeListTools) : that.beforeListTools == null;
        }

        @Override
        public final int hashCode() {
            int result = this.afterCallTool != null ? this.afterCallTool.hashCode() : 0;
            result = 31 * result + (this.afterListTools != null ? this.afterListTools.hashCode() : 0);
            result = 31 * result + (this.beforeCallTool != null ? this.beforeCallTool.hashCode() : 0);
            result = 31 * result + (this.beforeListTools != null ? this.beforeListTools.hashCode() : 0);
            return result;
        }
    }
}
