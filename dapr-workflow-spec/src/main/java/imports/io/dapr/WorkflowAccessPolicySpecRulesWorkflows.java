package imports.io.dapr;

/**
 * WorkflowRule grants the matched callers access to the listed operations on workflows whose name matches Name (exact or glob).
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.467Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.WorkflowAccessPolicySpecRulesWorkflows")
@software.amazon.jsii.Jsii.Proxy(WorkflowAccessPolicySpecRulesWorkflows.Jsii$Proxy.class)
public interface WorkflowAccessPolicySpecRulesWorkflows extends software.amazon.jsii.JsiiSerializable {

    /**
     * Name is the exact name or glob pattern for the workflow.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getName();

    /**
     * Operations is the set of operations this rule applies to.
     */
    @org.jetbrains.annotations.NotNull java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflowsOperations> getOperations();

    /**
     * @return a {@link Builder} of {@link WorkflowAccessPolicySpecRulesWorkflows}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link WorkflowAccessPolicySpecRulesWorkflows}
     */
    public static final class Builder implements software.amazon.jsii.Builder<WorkflowAccessPolicySpecRulesWorkflows> {
        java.lang.String name;
        java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflowsOperations> operations;

        /**
         * Sets the value of {@link WorkflowAccessPolicySpecRulesWorkflows#getName}
         * @param name Name is the exact name or glob pattern for the workflow. This parameter is required.
         * @return {@code this}
         */
        public Builder name(java.lang.String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the value of {@link WorkflowAccessPolicySpecRulesWorkflows#getOperations}
         * @param operations Operations is the set of operations this rule applies to. This parameter is required.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder operations(java.util.List<? extends imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflowsOperations> operations) {
            this.operations = (java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflowsOperations>)operations;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link WorkflowAccessPolicySpecRulesWorkflows}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public WorkflowAccessPolicySpecRulesWorkflows build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link WorkflowAccessPolicySpecRulesWorkflows}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements WorkflowAccessPolicySpecRulesWorkflows {
        private final java.lang.String name;
        private final java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflowsOperations> operations;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.operations = software.amazon.jsii.Kernel.get(this, "operations", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflowsOperations.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.name = java.util.Objects.requireNonNull(builder.name, "name is required");
            this.operations = (java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflowsOperations>)java.util.Objects.requireNonNull(builder.operations, "operations is required");
        }

        @Override
        public final java.lang.String getName() {
            return this.name;
        }

        @Override
        public final java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflowsOperations> getOperations() {
            return this.operations;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("name", om.valueToTree(this.getName()));
            data.set("operations", om.valueToTree(this.getOperations()));

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.WorkflowAccessPolicySpecRulesWorkflows"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            WorkflowAccessPolicySpecRulesWorkflows.Jsii$Proxy that = (WorkflowAccessPolicySpecRulesWorkflows.Jsii$Proxy) o;

            if (!name.equals(that.name)) return false;
            return this.operations.equals(that.operations);
        }

        @Override
        public final int hashCode() {
            int result = this.name.hashCode();
            result = 31 * result + (this.operations.hashCode());
            return result;
        }
    }
}
