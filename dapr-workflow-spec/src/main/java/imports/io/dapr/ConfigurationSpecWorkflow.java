package imports.io.dapr;

/**
 * WorkflowSpec defines the configuration for Dapr workflows.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.450Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecWorkflow")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecWorkflow.Jsii$Proxy.class)
public interface ConfigurationSpecWorkflow extends software.amazon.jsii.JsiiSerializable {

    /**
     * activityConcurrencyLimits defines per-activity-name concurrency limits enforced globally across all replicas by the scheduler.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.ConfigurationSpecWorkflowActivityConcurrencyLimits> getActivityConcurrencyLimits() {
        return null;
    }

    /**
     * globalMaxConcurrentActivityInvocations is the maximum number of concurrent activity invocations across all replicas, enforced by the scheduler.
     * <p>
     * If omitted, no global maximum will be enforced.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getGlobalMaxConcurrentActivityInvocations() {
        return null;
    }

    /**
     * globalMaxConcurrentWorkflowInvocations is the maximum number of concurrent workflow invocations across all replicas, enforced by the scheduler.
     * <p>
     * If omitted, no global maximum will be enforced.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getGlobalMaxConcurrentWorkflowInvocations() {
        return null;
    }

    /**
     * maxConcurrentActivityInvocations is the maximum number of concurrent activities that can be processed by a single Dapr instance.
     * <p>
     * Attempted invocations beyond this will be queued until the number of concurrent invocations drops below this value.
     * If omitted, no maximum will be enforced.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getMaxConcurrentActivityInvocations() {
        return null;
    }

    /**
     * maxConcurrentWorkflowInvocations is the maximum number of concurrent workflow invocations that can be scheduled by a single Dapr instance.
     * <p>
     * Attempted invocations beyond this will be queued until the number of concurrent invocations drops below this value.
     * If omitted, no maximum will be enforced.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getMaxConcurrentWorkflowInvocations() {
        return null;
    }

    /**
     * StateRetentionPolicy defines the retention configuration for workflow state once a workflow reaches a terminal state.
     * <p>
     * If not set, workflow
     * instances will not be automatically purged.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ConfigurationSpecWorkflowStateRetentionPolicy getStateRetentionPolicy() {
        return null;
    }

    /**
     * workflowConcurrencyLimits defines per-workflow-name concurrency limits enforced globally across all replicas by the scheduler.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.io.dapr.ConfigurationSpecWorkflowWorkflowConcurrencyLimits> getWorkflowConcurrencyLimits() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecWorkflow}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecWorkflow}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecWorkflow> {
        java.util.List<imports.io.dapr.ConfigurationSpecWorkflowActivityConcurrencyLimits> activityConcurrencyLimits;
        java.lang.Number globalMaxConcurrentActivityInvocations;
        java.lang.Number globalMaxConcurrentWorkflowInvocations;
        java.lang.Number maxConcurrentActivityInvocations;
        java.lang.Number maxConcurrentWorkflowInvocations;
        imports.io.dapr.ConfigurationSpecWorkflowStateRetentionPolicy stateRetentionPolicy;
        java.util.List<imports.io.dapr.ConfigurationSpecWorkflowWorkflowConcurrencyLimits> workflowConcurrencyLimits;

        /**
         * Sets the value of {@link ConfigurationSpecWorkflow#getActivityConcurrencyLimits}
         * @param activityConcurrencyLimits activityConcurrencyLimits defines per-activity-name concurrency limits enforced globally across all replicas by the scheduler.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder activityConcurrencyLimits(java.util.List<? extends imports.io.dapr.ConfigurationSpecWorkflowActivityConcurrencyLimits> activityConcurrencyLimits) {
            this.activityConcurrencyLimits = (java.util.List<imports.io.dapr.ConfigurationSpecWorkflowActivityConcurrencyLimits>)activityConcurrencyLimits;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecWorkflow#getGlobalMaxConcurrentActivityInvocations}
         * @param globalMaxConcurrentActivityInvocations globalMaxConcurrentActivityInvocations is the maximum number of concurrent activity invocations across all replicas, enforced by the scheduler.
         *                                               If omitted, no global maximum will be enforced.
         * @return {@code this}
         */
        public Builder globalMaxConcurrentActivityInvocations(java.lang.Number globalMaxConcurrentActivityInvocations) {
            this.globalMaxConcurrentActivityInvocations = globalMaxConcurrentActivityInvocations;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecWorkflow#getGlobalMaxConcurrentWorkflowInvocations}
         * @param globalMaxConcurrentWorkflowInvocations globalMaxConcurrentWorkflowInvocations is the maximum number of concurrent workflow invocations across all replicas, enforced by the scheduler.
         *                                               If omitted, no global maximum will be enforced.
         * @return {@code this}
         */
        public Builder globalMaxConcurrentWorkflowInvocations(java.lang.Number globalMaxConcurrentWorkflowInvocations) {
            this.globalMaxConcurrentWorkflowInvocations = globalMaxConcurrentWorkflowInvocations;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecWorkflow#getMaxConcurrentActivityInvocations}
         * @param maxConcurrentActivityInvocations maxConcurrentActivityInvocations is the maximum number of concurrent activities that can be processed by a single Dapr instance.
         *                                         Attempted invocations beyond this will be queued until the number of concurrent invocations drops below this value.
         *                                         If omitted, no maximum will be enforced.
         * @return {@code this}
         */
        public Builder maxConcurrentActivityInvocations(java.lang.Number maxConcurrentActivityInvocations) {
            this.maxConcurrentActivityInvocations = maxConcurrentActivityInvocations;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecWorkflow#getMaxConcurrentWorkflowInvocations}
         * @param maxConcurrentWorkflowInvocations maxConcurrentWorkflowInvocations is the maximum number of concurrent workflow invocations that can be scheduled by a single Dapr instance.
         *                                         Attempted invocations beyond this will be queued until the number of concurrent invocations drops below this value.
         *                                         If omitted, no maximum will be enforced.
         * @return {@code this}
         */
        public Builder maxConcurrentWorkflowInvocations(java.lang.Number maxConcurrentWorkflowInvocations) {
            this.maxConcurrentWorkflowInvocations = maxConcurrentWorkflowInvocations;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecWorkflow#getStateRetentionPolicy}
         * @param stateRetentionPolicy StateRetentionPolicy defines the retention configuration for workflow state once a workflow reaches a terminal state.
         *                             If not set, workflow
         *                             instances will not be automatically purged.
         * @return {@code this}
         */
        public Builder stateRetentionPolicy(imports.io.dapr.ConfigurationSpecWorkflowStateRetentionPolicy stateRetentionPolicy) {
            this.stateRetentionPolicy = stateRetentionPolicy;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecWorkflow#getWorkflowConcurrencyLimits}
         * @param workflowConcurrencyLimits workflowConcurrencyLimits defines per-workflow-name concurrency limits enforced globally across all replicas by the scheduler.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder workflowConcurrencyLimits(java.util.List<? extends imports.io.dapr.ConfigurationSpecWorkflowWorkflowConcurrencyLimits> workflowConcurrencyLimits) {
            this.workflowConcurrencyLimits = (java.util.List<imports.io.dapr.ConfigurationSpecWorkflowWorkflowConcurrencyLimits>)workflowConcurrencyLimits;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecWorkflow}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecWorkflow build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecWorkflow}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecWorkflow {
        private final java.util.List<imports.io.dapr.ConfigurationSpecWorkflowActivityConcurrencyLimits> activityConcurrencyLimits;
        private final java.lang.Number globalMaxConcurrentActivityInvocations;
        private final java.lang.Number globalMaxConcurrentWorkflowInvocations;
        private final java.lang.Number maxConcurrentActivityInvocations;
        private final java.lang.Number maxConcurrentWorkflowInvocations;
        private final imports.io.dapr.ConfigurationSpecWorkflowStateRetentionPolicy stateRetentionPolicy;
        private final java.util.List<imports.io.dapr.ConfigurationSpecWorkflowWorkflowConcurrencyLimits> workflowConcurrencyLimits;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.activityConcurrencyLimits = software.amazon.jsii.Kernel.get(this, "activityConcurrencyLimits", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecWorkflowActivityConcurrencyLimits.class)));
            this.globalMaxConcurrentActivityInvocations = software.amazon.jsii.Kernel.get(this, "globalMaxConcurrentActivityInvocations", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.globalMaxConcurrentWorkflowInvocations = software.amazon.jsii.Kernel.get(this, "globalMaxConcurrentWorkflowInvocations", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.maxConcurrentActivityInvocations = software.amazon.jsii.Kernel.get(this, "maxConcurrentActivityInvocations", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.maxConcurrentWorkflowInvocations = software.amazon.jsii.Kernel.get(this, "maxConcurrentWorkflowInvocations", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.stateRetentionPolicy = software.amazon.jsii.Kernel.get(this, "stateRetentionPolicy", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecWorkflowStateRetentionPolicy.class));
            this.workflowConcurrencyLimits = software.amazon.jsii.Kernel.get(this, "workflowConcurrencyLimits", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ConfigurationSpecWorkflowWorkflowConcurrencyLimits.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.activityConcurrencyLimits = (java.util.List<imports.io.dapr.ConfigurationSpecWorkflowActivityConcurrencyLimits>)builder.activityConcurrencyLimits;
            this.globalMaxConcurrentActivityInvocations = builder.globalMaxConcurrentActivityInvocations;
            this.globalMaxConcurrentWorkflowInvocations = builder.globalMaxConcurrentWorkflowInvocations;
            this.maxConcurrentActivityInvocations = builder.maxConcurrentActivityInvocations;
            this.maxConcurrentWorkflowInvocations = builder.maxConcurrentWorkflowInvocations;
            this.stateRetentionPolicy = builder.stateRetentionPolicy;
            this.workflowConcurrencyLimits = (java.util.List<imports.io.dapr.ConfigurationSpecWorkflowWorkflowConcurrencyLimits>)builder.workflowConcurrencyLimits;
        }

        @Override
        public final java.util.List<imports.io.dapr.ConfigurationSpecWorkflowActivityConcurrencyLimits> getActivityConcurrencyLimits() {
            return this.activityConcurrencyLimits;
        }

        @Override
        public final java.lang.Number getGlobalMaxConcurrentActivityInvocations() {
            return this.globalMaxConcurrentActivityInvocations;
        }

        @Override
        public final java.lang.Number getGlobalMaxConcurrentWorkflowInvocations() {
            return this.globalMaxConcurrentWorkflowInvocations;
        }

        @Override
        public final java.lang.Number getMaxConcurrentActivityInvocations() {
            return this.maxConcurrentActivityInvocations;
        }

        @Override
        public final java.lang.Number getMaxConcurrentWorkflowInvocations() {
            return this.maxConcurrentWorkflowInvocations;
        }

        @Override
        public final imports.io.dapr.ConfigurationSpecWorkflowStateRetentionPolicy getStateRetentionPolicy() {
            return this.stateRetentionPolicy;
        }

        @Override
        public final java.util.List<imports.io.dapr.ConfigurationSpecWorkflowWorkflowConcurrencyLimits> getWorkflowConcurrencyLimits() {
            return this.workflowConcurrencyLimits;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getActivityConcurrencyLimits() != null) {
                data.set("activityConcurrencyLimits", om.valueToTree(this.getActivityConcurrencyLimits()));
            }
            if (this.getGlobalMaxConcurrentActivityInvocations() != null) {
                data.set("globalMaxConcurrentActivityInvocations", om.valueToTree(this.getGlobalMaxConcurrentActivityInvocations()));
            }
            if (this.getGlobalMaxConcurrentWorkflowInvocations() != null) {
                data.set("globalMaxConcurrentWorkflowInvocations", om.valueToTree(this.getGlobalMaxConcurrentWorkflowInvocations()));
            }
            if (this.getMaxConcurrentActivityInvocations() != null) {
                data.set("maxConcurrentActivityInvocations", om.valueToTree(this.getMaxConcurrentActivityInvocations()));
            }
            if (this.getMaxConcurrentWorkflowInvocations() != null) {
                data.set("maxConcurrentWorkflowInvocations", om.valueToTree(this.getMaxConcurrentWorkflowInvocations()));
            }
            if (this.getStateRetentionPolicy() != null) {
                data.set("stateRetentionPolicy", om.valueToTree(this.getStateRetentionPolicy()));
            }
            if (this.getWorkflowConcurrencyLimits() != null) {
                data.set("workflowConcurrencyLimits", om.valueToTree(this.getWorkflowConcurrencyLimits()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecWorkflow"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecWorkflow.Jsii$Proxy that = (ConfigurationSpecWorkflow.Jsii$Proxy) o;

            if (this.activityConcurrencyLimits != null ? !this.activityConcurrencyLimits.equals(that.activityConcurrencyLimits) : that.activityConcurrencyLimits != null) return false;
            if (this.globalMaxConcurrentActivityInvocations != null ? !this.globalMaxConcurrentActivityInvocations.equals(that.globalMaxConcurrentActivityInvocations) : that.globalMaxConcurrentActivityInvocations != null) return false;
            if (this.globalMaxConcurrentWorkflowInvocations != null ? !this.globalMaxConcurrentWorkflowInvocations.equals(that.globalMaxConcurrentWorkflowInvocations) : that.globalMaxConcurrentWorkflowInvocations != null) return false;
            if (this.maxConcurrentActivityInvocations != null ? !this.maxConcurrentActivityInvocations.equals(that.maxConcurrentActivityInvocations) : that.maxConcurrentActivityInvocations != null) return false;
            if (this.maxConcurrentWorkflowInvocations != null ? !this.maxConcurrentWorkflowInvocations.equals(that.maxConcurrentWorkflowInvocations) : that.maxConcurrentWorkflowInvocations != null) return false;
            if (this.stateRetentionPolicy != null ? !this.stateRetentionPolicy.equals(that.stateRetentionPolicy) : that.stateRetentionPolicy != null) return false;
            return this.workflowConcurrencyLimits != null ? this.workflowConcurrencyLimits.equals(that.workflowConcurrencyLimits) : that.workflowConcurrencyLimits == null;
        }

        @Override
        public final int hashCode() {
            int result = this.activityConcurrencyLimits != null ? this.activityConcurrencyLimits.hashCode() : 0;
            result = 31 * result + (this.globalMaxConcurrentActivityInvocations != null ? this.globalMaxConcurrentActivityInvocations.hashCode() : 0);
            result = 31 * result + (this.globalMaxConcurrentWorkflowInvocations != null ? this.globalMaxConcurrentWorkflowInvocations.hashCode() : 0);
            result = 31 * result + (this.maxConcurrentActivityInvocations != null ? this.maxConcurrentActivityInvocations.hashCode() : 0);
            result = 31 * result + (this.maxConcurrentWorkflowInvocations != null ? this.maxConcurrentWorkflowInvocations.hashCode() : 0);
            result = 31 * result + (this.stateRetentionPolicy != null ? this.stateRetentionPolicy.hashCode() : 0);
            result = 31 * result + (this.workflowConcurrencyLimits != null ? this.workflowConcurrencyLimits.hashCode() : 0);
            return result;
        }
    }
}
