package imports.io.dapr;

/**
 * WorkflowAccessPolicySpec defines the desired state of WorkflowAccessPolicy.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.467Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.WorkflowAccessPolicySpec")
@software.amazon.jsii.Jsii.Proxy(WorkflowAccessPolicySpec.Jsii$Proxy.class)
public interface WorkflowAccessPolicySpec extends software.amazon.jsii.JsiiSerializable {

    /**
     * Rules defines the allow-list of which callers can perform which operations.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRules> getRules() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link WorkflowAccessPolicySpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link WorkflowAccessPolicySpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<WorkflowAccessPolicySpec> {
        java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRules> rules;

        /**
         * Sets the value of {@link WorkflowAccessPolicySpec#getRules}
         * @param rules Rules defines the allow-list of which callers can perform which operations.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder rules(java.util.List<? extends imports.io.dapr.WorkflowAccessPolicySpecRules> rules) {
            this.rules = (java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRules>)rules;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link WorkflowAccessPolicySpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public WorkflowAccessPolicySpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link WorkflowAccessPolicySpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements WorkflowAccessPolicySpec {
        private final java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRules> rules;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.rules = software.amazon.jsii.Kernel.get(this, "rules", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.WorkflowAccessPolicySpecRules.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.rules = (java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRules>)builder.rules;
        }

        @Override
        public final java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRules> getRules() {
            return this.rules;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getRules() != null) {
                data.set("rules", om.valueToTree(this.getRules()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.WorkflowAccessPolicySpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            WorkflowAccessPolicySpec.Jsii$Proxy that = (WorkflowAccessPolicySpec.Jsii$Proxy) o;

            return this.rules != null ? this.rules.equals(that.rules) : that.rules == null;
        }

        @Override
        public final int hashCode() {
            int result = this.rules != null ? this.rules.hashCode() : 0;
            return result;
        }
    }
}
