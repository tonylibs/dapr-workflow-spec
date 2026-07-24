package imports.io.dapr;

/**
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.464Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ResiliencySpecTargets")
@software.amazon.jsii.Jsii.Proxy(ResiliencySpecTargets.Jsii$Proxy.class)
public interface ResiliencySpecTargets extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsActors> getActors() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsApps> getApps() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsComponents> getComponents() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ResiliencySpecTargets}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ResiliencySpecTargets}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ResiliencySpecTargets> {
        java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsActors> actors;
        java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsApps> apps;
        java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsComponents> components;

        /**
         * Sets the value of {@link ResiliencySpecTargets#getActors}
         * @param actors the value to be set.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder actors(java.util.Map<java.lang.String, ? extends imports.io.dapr.ResiliencySpecTargetsActors> actors) {
            this.actors = (java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsActors>)actors;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpecTargets#getApps}
         * @param apps the value to be set.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder apps(java.util.Map<java.lang.String, ? extends imports.io.dapr.ResiliencySpecTargetsApps> apps) {
            this.apps = (java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsApps>)apps;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpecTargets#getComponents}
         * @param components the value to be set.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder components(java.util.Map<java.lang.String, ? extends imports.io.dapr.ResiliencySpecTargetsComponents> components) {
            this.components = (java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsComponents>)components;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ResiliencySpecTargets}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ResiliencySpecTargets build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ResiliencySpecTargets}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ResiliencySpecTargets {
        private final java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsActors> actors;
        private final java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsApps> apps;
        private final java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsComponents> components;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.actors = software.amazon.jsii.Kernel.get(this, "actors", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ResiliencySpecTargetsActors.class)));
            this.apps = software.amazon.jsii.Kernel.get(this, "apps", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ResiliencySpecTargetsApps.class)));
            this.components = software.amazon.jsii.Kernel.get(this, "components", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(imports.io.dapr.ResiliencySpecTargetsComponents.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.actors = (java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsActors>)builder.actors;
            this.apps = (java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsApps>)builder.apps;
            this.components = (java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsComponents>)builder.components;
        }

        @Override
        public final java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsActors> getActors() {
            return this.actors;
        }

        @Override
        public final java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsApps> getApps() {
            return this.apps;
        }

        @Override
        public final java.util.Map<java.lang.String, imports.io.dapr.ResiliencySpecTargetsComponents> getComponents() {
            return this.components;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getActors() != null) {
                data.set("actors", om.valueToTree(this.getActors()));
            }
            if (this.getApps() != null) {
                data.set("apps", om.valueToTree(this.getApps()));
            }
            if (this.getComponents() != null) {
                data.set("components", om.valueToTree(this.getComponents()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ResiliencySpecTargets"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ResiliencySpecTargets.Jsii$Proxy that = (ResiliencySpecTargets.Jsii$Proxy) o;

            if (this.actors != null ? !this.actors.equals(that.actors) : that.actors != null) return false;
            if (this.apps != null ? !this.apps.equals(that.apps) : that.apps != null) return false;
            return this.components != null ? this.components.equals(that.components) : that.components == null;
        }

        @Override
        public final int hashCode() {
            int result = this.actors != null ? this.actors.hashCode() : 0;
            result = 31 * result + (this.apps != null ? this.apps.hashCode() : 0);
            result = 31 * result + (this.components != null ? this.components.hashCode() : 0);
            return result;
        }
    }
}
