package imports.io.dapr;

/**
 * StateRetentionPolicy defines the retention configuration for workflow state once a workflow reaches a terminal state.
 * <p>
 * If not set, workflow
 * instances will not be automatically purged.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.450Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecWorkflowStateRetentionPolicy")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecWorkflowStateRetentionPolicy.Jsii$Proxy.class)
public interface ConfigurationSpecWorkflowStateRetentionPolicy extends software.amazon.jsii.JsiiSerializable {

    /**
     * AnyTerminal is the TTL for purging workflow instances that reach any terminal state.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getAnyTerminal() {
        return null;
    }

    /**
     * Completed is the TTL for purging workflow instances that reach the Completed terminal state.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getCompleted() {
        return null;
    }

    /**
     * Failed is the TTL for purging workflow instances that reach the Failed terminal state.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getFailed() {
        return null;
    }

    /**
     * Terminated is the TTL for purging workflow instances that reach the Terminated terminal state.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getTerminated() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecWorkflowStateRetentionPolicy}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecWorkflowStateRetentionPolicy}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecWorkflowStateRetentionPolicy> {
        java.lang.String anyTerminal;
        java.lang.String completed;
        java.lang.String failed;
        java.lang.String terminated;

        /**
         * Sets the value of {@link ConfigurationSpecWorkflowStateRetentionPolicy#getAnyTerminal}
         * @param anyTerminal AnyTerminal is the TTL for purging workflow instances that reach any terminal state.
         * @return {@code this}
         */
        public Builder anyTerminal(java.lang.String anyTerminal) {
            this.anyTerminal = anyTerminal;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecWorkflowStateRetentionPolicy#getCompleted}
         * @param completed Completed is the TTL for purging workflow instances that reach the Completed terminal state.
         * @return {@code this}
         */
        public Builder completed(java.lang.String completed) {
            this.completed = completed;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecWorkflowStateRetentionPolicy#getFailed}
         * @param failed Failed is the TTL for purging workflow instances that reach the Failed terminal state.
         * @return {@code this}
         */
        public Builder failed(java.lang.String failed) {
            this.failed = failed;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecWorkflowStateRetentionPolicy#getTerminated}
         * @param terminated Terminated is the TTL for purging workflow instances that reach the Terminated terminal state.
         * @return {@code this}
         */
        public Builder terminated(java.lang.String terminated) {
            this.terminated = terminated;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecWorkflowStateRetentionPolicy}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecWorkflowStateRetentionPolicy build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecWorkflowStateRetentionPolicy}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecWorkflowStateRetentionPolicy {
        private final java.lang.String anyTerminal;
        private final java.lang.String completed;
        private final java.lang.String failed;
        private final java.lang.String terminated;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.anyTerminal = software.amazon.jsii.Kernel.get(this, "anyTerminal", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.completed = software.amazon.jsii.Kernel.get(this, "completed", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.failed = software.amazon.jsii.Kernel.get(this, "failed", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.terminated = software.amazon.jsii.Kernel.get(this, "terminated", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.anyTerminal = builder.anyTerminal;
            this.completed = builder.completed;
            this.failed = builder.failed;
            this.terminated = builder.terminated;
        }

        @Override
        public final java.lang.String getAnyTerminal() {
            return this.anyTerminal;
        }

        @Override
        public final java.lang.String getCompleted() {
            return this.completed;
        }

        @Override
        public final java.lang.String getFailed() {
            return this.failed;
        }

        @Override
        public final java.lang.String getTerminated() {
            return this.terminated;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getAnyTerminal() != null) {
                data.set("anyTerminal", om.valueToTree(this.getAnyTerminal()));
            }
            if (this.getCompleted() != null) {
                data.set("completed", om.valueToTree(this.getCompleted()));
            }
            if (this.getFailed() != null) {
                data.set("failed", om.valueToTree(this.getFailed()));
            }
            if (this.getTerminated() != null) {
                data.set("terminated", om.valueToTree(this.getTerminated()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecWorkflowStateRetentionPolicy"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecWorkflowStateRetentionPolicy.Jsii$Proxy that = (ConfigurationSpecWorkflowStateRetentionPolicy.Jsii$Proxy) o;

            if (this.anyTerminal != null ? !this.anyTerminal.equals(that.anyTerminal) : that.anyTerminal != null) return false;
            if (this.completed != null ? !this.completed.equals(that.completed) : that.completed != null) return false;
            if (this.failed != null ? !this.failed.equals(that.failed) : that.failed != null) return false;
            return this.terminated != null ? this.terminated.equals(that.terminated) : that.terminated == null;
        }

        @Override
        public final int hashCode() {
            int result = this.anyTerminal != null ? this.anyTerminal.hashCode() : 0;
            result = 31 * result + (this.completed != null ? this.completed.hashCode() : 0);
            result = 31 * result + (this.failed != null ? this.failed.hashCode() : 0);
            result = 31 * result + (this.terminated != null ? this.terminated.hashCode() : 0);
            return result;
        }
    }
}
