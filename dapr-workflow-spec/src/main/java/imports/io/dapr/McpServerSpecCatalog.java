package imports.io.dapr;

/**
 * Catalog holds user-facing governance metadata (display name, description, owner, tags, links).
 * <p>
 * It is purely informational and not used at runtime.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.456Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.McpServerSpecCatalog")
@software.amazon.jsii.Jsii.Proxy(McpServerSpecCatalog.Jsii$Proxy.class)
public interface McpServerSpecCatalog extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getDescription() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getDisplayName() {
        return null;
    }

    /**
     * Links is a free-form map of named URLs (e.g. "docs", "runbook", "dashboard").
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, java.lang.String> getLinks() {
        return null;
    }

    /**
     * MCPCatalogOwner identifies the team responsible for the MCP server.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.McpServerSpecCatalogOwner getOwner() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getTags() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link McpServerSpecCatalog}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link McpServerSpecCatalog}
     */
    public static final class Builder implements software.amazon.jsii.Builder<McpServerSpecCatalog> {
        java.lang.String description;
        java.lang.String displayName;
        java.util.Map<java.lang.String, java.lang.String> links;
        imports.io.dapr.McpServerSpecCatalogOwner owner;
        java.util.List<java.lang.String> tags;

        /**
         * Sets the value of {@link McpServerSpecCatalog#getDescription}
         * @param description the value to be set.
         * @return {@code this}
         */
        public Builder description(java.lang.String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecCatalog#getDisplayName}
         * @param displayName the value to be set.
         * @return {@code this}
         */
        public Builder displayName(java.lang.String displayName) {
            this.displayName = displayName;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecCatalog#getLinks}
         * @param links Links is a free-form map of named URLs (e.g. "docs", "runbook", "dashboard").
         * @return {@code this}
         */
        public Builder links(java.util.Map<java.lang.String, java.lang.String> links) {
            this.links = links;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecCatalog#getOwner}
         * @param owner MCPCatalogOwner identifies the team responsible for the MCP server.
         * @return {@code this}
         */
        public Builder owner(imports.io.dapr.McpServerSpecCatalogOwner owner) {
            this.owner = owner;
            return this;
        }

        /**
         * Sets the value of {@link McpServerSpecCatalog#getTags}
         * @param tags the value to be set.
         * @return {@code this}
         */
        public Builder tags(java.util.List<java.lang.String> tags) {
            this.tags = tags;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link McpServerSpecCatalog}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public McpServerSpecCatalog build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link McpServerSpecCatalog}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements McpServerSpecCatalog {
        private final java.lang.String description;
        private final java.lang.String displayName;
        private final java.util.Map<java.lang.String, java.lang.String> links;
        private final imports.io.dapr.McpServerSpecCatalogOwner owner;
        private final java.util.List<java.lang.String> tags;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.description = software.amazon.jsii.Kernel.get(this, "description", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.displayName = software.amazon.jsii.Kernel.get(this, "displayName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.links = software.amazon.jsii.Kernel.get(this, "links", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.owner = software.amazon.jsii.Kernel.get(this, "owner", software.amazon.jsii.NativeType.forClass(imports.io.dapr.McpServerSpecCatalogOwner.class));
            this.tags = software.amazon.jsii.Kernel.get(this, "tags", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.description = builder.description;
            this.displayName = builder.displayName;
            this.links = builder.links;
            this.owner = builder.owner;
            this.tags = builder.tags;
        }

        @Override
        public final java.lang.String getDescription() {
            return this.description;
        }

        @Override
        public final java.lang.String getDisplayName() {
            return this.displayName;
        }

        @Override
        public final java.util.Map<java.lang.String, java.lang.String> getLinks() {
            return this.links;
        }

        @Override
        public final imports.io.dapr.McpServerSpecCatalogOwner getOwner() {
            return this.owner;
        }

        @Override
        public final java.util.List<java.lang.String> getTags() {
            return this.tags;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getDescription() != null) {
                data.set("description", om.valueToTree(this.getDescription()));
            }
            if (this.getDisplayName() != null) {
                data.set("displayName", om.valueToTree(this.getDisplayName()));
            }
            if (this.getLinks() != null) {
                data.set("links", om.valueToTree(this.getLinks()));
            }
            if (this.getOwner() != null) {
                data.set("owner", om.valueToTree(this.getOwner()));
            }
            if (this.getTags() != null) {
                data.set("tags", om.valueToTree(this.getTags()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.McpServerSpecCatalog"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            McpServerSpecCatalog.Jsii$Proxy that = (McpServerSpecCatalog.Jsii$Proxy) o;

            if (this.description != null ? !this.description.equals(that.description) : that.description != null) return false;
            if (this.displayName != null ? !this.displayName.equals(that.displayName) : that.displayName != null) return false;
            if (this.links != null ? !this.links.equals(that.links) : that.links != null) return false;
            if (this.owner != null ? !this.owner.equals(that.owner) : that.owner != null) return false;
            return this.tags != null ? this.tags.equals(that.tags) : that.tags == null;
        }

        @Override
        public final int hashCode() {
            int result = this.description != null ? this.description.hashCode() : 0;
            result = 31 * result + (this.displayName != null ? this.displayName.hashCode() : 0);
            result = 31 * result + (this.links != null ? this.links.hashCode() : 0);
            result = 31 * result + (this.owner != null ? this.owner.hashCode() : 0);
            result = 31 * result + (this.tags != null ? this.tags.hashCode() : 0);
            return result;
        }
    }
}
