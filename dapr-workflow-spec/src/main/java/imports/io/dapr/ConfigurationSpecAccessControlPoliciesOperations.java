package imports.io.dapr;

/**
 * AppOperationAction defines the data structure for each app operation.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.438Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecAccessControlPoliciesOperations")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecAccessControlPoliciesOperations.Jsii$Proxy.class)
public interface ConfigurationSpecAccessControlPoliciesOperations extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getAction();

    /**
     */
    @org.jetbrains.annotations.NotNull java.lang.String getName();

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getHttpVerb() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecAccessControlPoliciesOperations}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecAccessControlPoliciesOperations}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecAccessControlPoliciesOperations> {
        java.lang.String action;
        java.lang.String name;
        java.util.List<java.lang.String> httpVerb;

        /**
         * Sets the value of {@link ConfigurationSpecAccessControlPoliciesOperations#getAction}
         * @param action the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder action(java.lang.String action) {
            this.action = action;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecAccessControlPoliciesOperations#getName}
         * @param name the value to be set. This parameter is required.
         * @return {@code this}
         */
        public Builder name(java.lang.String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecAccessControlPoliciesOperations#getHttpVerb}
         * @param httpVerb the value to be set.
         * @return {@code this}
         */
        public Builder httpVerb(java.util.List<java.lang.String> httpVerb) {
            this.httpVerb = httpVerb;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecAccessControlPoliciesOperations}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecAccessControlPoliciesOperations build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecAccessControlPoliciesOperations}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecAccessControlPoliciesOperations {
        private final java.lang.String action;
        private final java.lang.String name;
        private final java.util.List<java.lang.String> httpVerb;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.action = software.amazon.jsii.Kernel.get(this, "action", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.httpVerb = software.amazon.jsii.Kernel.get(this, "httpVerb", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.action = java.util.Objects.requireNonNull(builder.action, "action is required");
            this.name = java.util.Objects.requireNonNull(builder.name, "name is required");
            this.httpVerb = builder.httpVerb;
        }

        @Override
        public final java.lang.String getAction() {
            return this.action;
        }

        @Override
        public final java.lang.String getName() {
            return this.name;
        }

        @Override
        public final java.util.List<java.lang.String> getHttpVerb() {
            return this.httpVerb;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("action", om.valueToTree(this.getAction()));
            data.set("name", om.valueToTree(this.getName()));
            if (this.getHttpVerb() != null) {
                data.set("httpVerb", om.valueToTree(this.getHttpVerb()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecAccessControlPoliciesOperations"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecAccessControlPoliciesOperations.Jsii$Proxy that = (ConfigurationSpecAccessControlPoliciesOperations.Jsii$Proxy) o;

            if (!action.equals(that.action)) return false;
            if (!name.equals(that.name)) return false;
            return this.httpVerb != null ? this.httpVerb.equals(that.httpVerb) : that.httpVerb == null;
        }

        @Override
        public final int hashCode() {
            int result = this.action.hashCode();
            result = 31 * result + (this.name.hashCode());
            result = 31 * result + (this.httpVerb != null ? this.httpVerb.hashCode() : 0);
            return result;
        }
    }
}
