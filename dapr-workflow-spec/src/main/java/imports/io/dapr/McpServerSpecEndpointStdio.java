package imports.io.dapr;

/**
 * Stdio holds configuration for the stdio subprocess transport.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.460Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.McpServerSpecEndpointStdio")
@software.amazon.jsii.Jsii.Proxy(McpServerSpecEndpointStdio.Jsii$Proxy.class)
public interface McpServerSpecEndpointStdio extends software.amazon.jsii.JsiiSerializable {

    /**
     * Command is the executable to run.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getCommand();

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getArgs() {
        return null;
    }

    /**
     * Env are environment variables injected into the subprocess.
     * <p>
     * Supports plain value, secretKeyRef, and envRef.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.McpServerSpecEndpointStdioEnv> getEnv() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link McpServerSpecEndpointStdio}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link McpServerSpecEndpointStdio}
     */
    public static final class Builder implements software.amazon.jsii.Builder<McpServerSpecEndpointStdio> {
        java.lang.String command;
        java.util.List<java.lang.String> args;
        java.util.List<imports.io.dapr.McpServerSpecEndpointStdioEnv> env;

        /**
         * Sets the value of {@link McpServerSpecEndpointStdio#getCommand}
         * @param command Command is the executable to run. This parameter is required.
         * @return {@code this}
         */
        public Builder command(java.lang.String command) {
            this.command = command;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpointStdio#getArgs}
         * @param args the value to be set.
         * @return {@code this}
         */
        public Builder args(java.util.List<java.lang.String> args) {
            this.args = args;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecEndpointStdio#getEnv}
         * @param env Env are environment variables injected into the subprocess.
         *            Supports plain value, secretKeyRef, and envRef.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder env(java.util.List<? extends imports.io.dapr.McpServerSpecEndpointStdioEnv> env) {
            this.env = (java.util.List<imports.io.dapr.McpServerSpecEndpointStdioEnv>)env;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link McpServerSpecEndpointStdio}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public McpServerSpecEndpointStdio build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link McpServerSpecEndpointStdio}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements McpServerSpecEndpointStdio {
        private final java.lang.String command;
        private final java.util.List<java.lang.String> args;
        private final java.util.List<imports.io.dapr.McpServerSpecEndpointStdioEnv> env;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.command = software.amazon.jsii.Kernel.get(this, "command", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.args = software.amazon.jsii.Kernel.get(this, "args", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.env = software.amazon.jsii.Kernel.get(this, "env", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecEndpointStdioEnv.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.command = java.util.Objects.requireNonNull(builder.command, "command is required");
            this.args = builder.args;
            this.env = (java.util.List<imports.io.dapr.McpServerSpecEndpointStdioEnv>)builder.env;
        }

        @Override
        public final java.lang.String getCommand() {
            return this.command;
        }

        @Override
        public final java.util.List<java.lang.String> getArgs() {
            return this.args;
        }

        @Override
        public final java.util.List<imports.io.dapr.McpServerSpecEndpointStdioEnv> getEnv() {
            return this.env;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("command", om.valueToTree(this.getCommand()));
            if (this.getArgs() != null) {
                data.set("args", om.valueToTree(this.getArgs()));
            }
            if (this.getEnv() != null) {
                data.set("env", om.valueToTree(this.getEnv()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.McpServerSpecEndpointStdio"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            McpServerSpecEndpointStdio.Jsii$Proxy that = (McpServerSpecEndpointStdio.Jsii$Proxy) o;

            if (!command.equals(that.command)) return false;
            if (this.args != null ? !this.args.equals(that.args) : that.args != null) return false;
            return this.env != null ? this.env.equals(that.env) : that.env == null;
        }

        @Override
        public final int hashCode() {
            int result = this.command.hashCode();
            result = 31 * result + (this.args != null ? this.args.hashCode() : 0);
            result = 31 * result + (this.env != null ? this.env.hashCode() : 0);
            return result;
        }
    }
}
