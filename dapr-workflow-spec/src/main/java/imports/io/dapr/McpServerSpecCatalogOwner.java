package imports.io.dapr;

/**
 * MCPCatalogOwner identifies the team responsible for the MCP server.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.457Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.McpServerSpecCatalogOwner")
@software.amazon.jsii.Jsii.Proxy(McpServerSpecCatalogOwner.Jsii$Proxy.class)
public interface McpServerSpecCatalogOwner extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getContact() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getTeam() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link McpServerSpecCatalogOwner}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link McpServerSpecCatalogOwner}
     */
    public static final class Builder implements software.amazon.jsii.Builder<McpServerSpecCatalogOwner> {
        java.lang.String contact;
        java.lang.String team;

        /**
         * Sets the value of {@link McpServerSpecCatalogOwner#getContact}
         * @param contact the value to be set.
         * @return {@code this}
         */
        public Builder contact(java.lang.String contact) {
            this.contact = contact;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecCatalogOwner#getTeam}
         * @param team the value to be set.
         * @return {@code this}
         */
        public Builder team(java.lang.String team) {
            this.team = team;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link McpServerSpecCatalogOwner}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public McpServerSpecCatalogOwner build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link McpServerSpecCatalogOwner}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements McpServerSpecCatalogOwner {
        private final java.lang.String contact;
        private final java.lang.String team;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.contact = software.amazon.jsii.Kernel.get(this, "contact", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.team = software.amazon.jsii.Kernel.get(this, "team", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.contact = builder.contact;
            this.team = builder.team;
        }

        @Override
        public final java.lang.String getContact() {
            return this.contact;
        }

        @Override
        public final java.lang.String getTeam() {
            return this.team;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getContact() != null) {
                data.set("contact", om.valueToTree(this.getContact()));
            }
            if (this.getTeam() != null) {
                data.set("team", om.valueToTree(this.getTeam()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.McpServerSpecCatalogOwner"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            McpServerSpecCatalogOwner.Jsii$Proxy that = (McpServerSpecCatalogOwner.Jsii$Proxy) o;

            if (this.contact != null ? !this.contact.equals(that.contact) : that.contact != null) return false;
            return this.team != null ? this.team.equals(that.team) : that.team == null;
        }

        @Override
        public final int hashCode() {
            int result = this.contact != null ? this.contact.hashCode() : 0;
            result = 31 * result + (this.team != null ? this.team.hashCode() : 0);
            return result;
        }
    }
}
