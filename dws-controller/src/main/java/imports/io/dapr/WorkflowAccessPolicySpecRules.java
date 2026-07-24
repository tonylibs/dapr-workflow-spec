package imports.io.dapr;

/**
 * WorkflowAccessPolicyRule grants the listed callers access to the listed workflows and/or activities.
 * <p>
 * Presence of a matching rule grants access; if
 * no rule matches, the call is denied. At least one of workflows or
 * activities must be set.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.467Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.WorkflowAccessPolicySpecRules")
@software.amazon.jsii.Jsii.Proxy(WorkflowAccessPolicySpecRules.Jsii$Proxy.class)
public interface WorkflowAccessPolicySpecRules extends software.amazon.jsii.JsiiSerializable {

    /**
     * Callers that this rule applies to.
     */
    @org.jetbrains.annotations.NotNull java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesCallers> getCallers();

    /**
     * Activities are the activity rules that the matched callers are allowed.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesActivities> getActivities() {
        return null;
    }

    /**
     * Workflows are the workflow rules that the matched callers are allowed.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflows> getWorkflows() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link WorkflowAccessPolicySpecRules}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link WorkflowAccessPolicySpecRules}
     */
    public static final class Builder implements software.amazon.jsii.Builder<WorkflowAccessPolicySpecRules> {
        java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesCallers> callers;
        java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesActivities> activities;
        java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflows> workflows;

        /**
         * Sets the value of {@link WorkflowAccessPolicySpecRules#getCallers}
         * @param callers Callers that this rule applies to. This parameter is required.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder callers(java.util.List<? extends imports.io.dapr.WorkflowAccessPolicySpecRulesCallers> callers) {
            this.callers = (java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesCallers>)callers;
            return this;
        }

        /**
         * Sets the value of {@link WorkflowAccessPolicySpecRules#getActivities}
         * @param activities Activities are the activity rules that the matched callers are allowed.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder activities(java.util.List<? extends imports.io.dapr.WorkflowAccessPolicySpecRulesActivities> activities) {
            this.activities = (java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesActivities>)activities;
            return this;
        }

        /**
         * Sets the value of {@link WorkflowAccessPolicySpecRules#getWorkflows}
         * @param workflows Workflows are the workflow rules that the matched callers are allowed.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder workflows(java.util.List<? extends imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflows> workflows) {
            this.workflows = (java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflows>)workflows;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link WorkflowAccessPolicySpecRules}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public WorkflowAccessPolicySpecRules build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link WorkflowAccessPolicySpecRules}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements WorkflowAccessPolicySpecRules {
        private final java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesCallers> callers;
        private final java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesActivities> activities;
        private final java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflows> workflows;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.callers = software.amazon.jsii.Kernel.get(this, "callers", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.WorkflowAccessPolicySpecRulesCallers.class)));
            this.activities = software.amazon.jsii.Kernel.get(this, "activities", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.WorkflowAccessPolicySpecRulesActivities.class)));
            this.workflows = software.amazon.jsii.Kernel.get(this, "workflows", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflows.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.callers = (java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesCallers>)java.util.Objects.requireNonNull(builder.callers, "callers is required");
            this.activities = (java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesActivities>)builder.activities;
            this.workflows = (java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflows>)builder.workflows;
        }

        @Override
        public final java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesCallers> getCallers() {
            return this.callers;
        }

        @Override
        public final java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesActivities> getActivities() {
            return this.activities;
        }

        @Override
        public final java.util.List<imports.io.dapr.WorkflowAccessPolicySpecRulesWorkflows> getWorkflows() {
            return this.workflows;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("callers", om.valueToTree(this.getCallers()));
            if (this.getActivities() != null) {
                data.set("activities", om.valueToTree(this.getActivities()));
            }
            if (this.getWorkflows() != null) {
                data.set("workflows", om.valueToTree(this.getWorkflows()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.WorkflowAccessPolicySpecRules"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            WorkflowAccessPolicySpecRules.Jsii$Proxy that = (WorkflowAccessPolicySpecRules.Jsii$Proxy) o;

            if (!callers.equals(that.callers)) return false;
            if (this.activities != null ? !this.activities.equals(that.activities) : that.activities != null) return false;
            return this.workflows != null ? this.workflows.equals(that.workflows) : that.workflows == null;
        }

        @Override
        public final int hashCode() {
            int result = this.callers.hashCode();
            result = 31 * result + (this.activities != null ? this.activities.hashCode() : 0);
            result = 31 * result + (this.workflows != null ? this.workflows.hashCode() : 0);
            return result;
        }
    }
}
