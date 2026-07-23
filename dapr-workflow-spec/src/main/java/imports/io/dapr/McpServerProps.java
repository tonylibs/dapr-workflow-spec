package imports.io.dapr;

/**
 * MCPServer describes a Dapr MCPServer resource that declares a connection to an MCP (Model Context Protocol) server for durable tool execution.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.454Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.McpServerProps")
@software.amazon.jsii.Jsii.Proxy(McpServerProps.Jsii$Proxy.class)
public interface McpServerProps extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    default @org.jetbrains.annotations.Nullable org.cdk8s.ApiObjectMetadata getMetadata() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getScopes() {
        return null;
    }

    /**
     * MCPServerSpec is the full configuration for an MCP server connection.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.McpServerSpec getSpec() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link McpServerProps}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link McpServerProps}
     */
    public static final class Builder implements software.amazon.jsii.Builder<McpServerProps> {
        org.cdk8s.ApiObjectMetadata metadata;
        java.util.List<java.lang.String> scopes;
        imports.io.dapr.McpServerSpec spec;

        /**
         * Sets the value of {@link McpServerProps#getMetadata}
         * @param metadata the value to be set.
         * @return {@code this}
         */
        public Builder metadata(org.cdk8s.ApiObjectMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Sets the value of {@link McpServerProps#getScopes}
         * @param scopes the value to be set.
         * @return {@code this}
         */
        public Builder scopes(java.util.List<java.lang.String> scopes) {
            this.scopes = scopes;
            return this;
        }

        /**
         * Sets the value of {@link McpServerProps#getSpec}
         * @param spec MCPServerSpec is the full configuration for an MCP server connection.
         * @return {@code this}
         */
        public Builder spec(imports.io.dapr.McpServerSpec spec) {
            this.spec = spec;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link McpServerProps}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public McpServerProps build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link McpServerProps}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements McpServerProps {
        private final org.cdk8s.ApiObjectMetadata metadata;
        private final java.util.List<java.lang.String> scopes;
        private final imports.io.dapr.McpServerSpec spec;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.metadata = software.amazon.jsii.Kernel.get(this, "metadata", software.amazon.jsii.NativeType.forClass(org.cdk8s.ApiObjectMetadata.class));
            this.scopes = software.amazon.jsii.Kernel.get(this, "scopes", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.spec = software.amazon.jsii.Kernel.get(this, "spec", software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpec.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.metadata = builder.metadata;
            this.scopes = builder.scopes;
            this.spec = builder.spec;
        }

        @Override
        public final org.cdk8s.ApiObjectMetadata getMetadata() {
            return this.metadata;
        }

        @Override
        public final java.util.List<java.lang.String> getScopes() {
            return this.scopes;
        }

        @Override
        public final imports.io.dapr.McpServerSpec getSpec() {
            return this.spec;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getMetadata() != null) {
                data.set("metadata", om.valueToTree(this.getMetadata()));
            }
            if (this.getScopes() != null) {
                data.set("scopes", om.valueToTree(this.getScopes()));
            }
            if (this.getSpec() != null) {
                data.set("spec", om.valueToTree(this.getSpec()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.McpServerProps"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            McpServerProps.Jsii$Proxy that = (McpServerProps.Jsii$Proxy) o;

            if (this.metadata != null ? !this.metadata.equals(that.metadata) : that.metadata != null) return false;
            if (this.scopes != null ? !this.scopes.equals(that.scopes) : that.scopes != null) return false;
            return this.spec != null ? this.spec.equals(that.spec) : that.spec == null;
        }

        @Override
        public final int hashCode() {
            int result = this.metadata != null ? this.metadata.hashCode() : 0;
            result = 31 * result + (this.scopes != null ? this.scopes.hashCode() : 0);
            result = 31 * result + (this.spec != null ? this.spec.hashCode() : 0);
            return result;
        }
    }
}
