package imports.dev.knative.serving;

/**
 * Source for the environment variable's value.
 * <p>
 * Cannot be used if value is not empty.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.112Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ConfigurationSpecTemplateSpecContainersEnvValueFrom")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecTemplateSpecContainersEnvValueFrom.Jsii$Proxy.class)
public interface ConfigurationSpecTemplateSpecContainersEnvValueFrom extends software.amazon.jsii.JsiiSerializable {

    /**
     * Selects a key of a ConfigMap.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvValueFromConfigMapKeyRef getConfigMapKeyRef() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-fieldref.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Object getFieldRef() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-fieldref.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Object getResourceFieldRef() {
        return null;
    }

    /**
     * Selects a key of a secret in the pod's namespace.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvValueFromSecretKeyRef getSecretKeyRef() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecTemplateSpecContainersEnvValueFrom}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecTemplateSpecContainersEnvValueFrom}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecTemplateSpecContainersEnvValueFrom> {
        imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvValueFromConfigMapKeyRef configMapKeyRef;
        java.lang.Object fieldRef;
        java.lang.Object resourceFieldRef;
        imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvValueFromSecretKeyRef secretKeyRef;

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersEnvValueFrom#getConfigMapKeyRef}
         * @param configMapKeyRef Selects a key of a ConfigMap.
         * @return {@code this}
         */
        public Builder configMapKeyRef(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvValueFromConfigMapKeyRef configMapKeyRef) {
            this.configMapKeyRef = configMapKeyRef;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersEnvValueFrom#getFieldRef}
         * @param fieldRef This is accessible behind a feature flag - kubernetes.podspec-fieldref.
         * @return {@code this}
         */
        public Builder fieldRef(java.lang.Object fieldRef) {
            this.fieldRef = fieldRef;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersEnvValueFrom#getResourceFieldRef}
         * @param resourceFieldRef This is accessible behind a feature flag - kubernetes.podspec-fieldref.
         * @return {@code this}
         */
        public Builder resourceFieldRef(java.lang.Object resourceFieldRef) {
            this.resourceFieldRef = resourceFieldRef;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersEnvValueFrom#getSecretKeyRef}
         * @param secretKeyRef Selects a key of a secret in the pod's namespace.
         * @return {@code this}
         */
        public Builder secretKeyRef(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvValueFromSecretKeyRef secretKeyRef) {
            this.secretKeyRef = secretKeyRef;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecTemplateSpecContainersEnvValueFrom}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecTemplateSpecContainersEnvValueFrom build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecTemplateSpecContainersEnvValueFrom}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecTemplateSpecContainersEnvValueFrom {
        private final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvValueFromConfigMapKeyRef configMapKeyRef;
        private final java.lang.Object fieldRef;
        private final java.lang.Object resourceFieldRef;
        private final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvValueFromSecretKeyRef secretKeyRef;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.configMapKeyRef = software.amazon.jsii.Kernel.get(this, "configMapKeyRef", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvValueFromConfigMapKeyRef.class));
            this.fieldRef = software.amazon.jsii.Kernel.get(this, "fieldRef", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
            this.resourceFieldRef = software.amazon.jsii.Kernel.get(this, "resourceFieldRef", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
            this.secretKeyRef = software.amazon.jsii.Kernel.get(this, "secretKeyRef", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvValueFromSecretKeyRef.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.configMapKeyRef = builder.configMapKeyRef;
            this.fieldRef = builder.fieldRef;
            this.resourceFieldRef = builder.resourceFieldRef;
            this.secretKeyRef = builder.secretKeyRef;
        }

        @Override
        public final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvValueFromConfigMapKeyRef getConfigMapKeyRef() {
            return this.configMapKeyRef;
        }

        @Override
        public final java.lang.Object getFieldRef() {
            return this.fieldRef;
        }

        @Override
        public final java.lang.Object getResourceFieldRef() {
            return this.resourceFieldRef;
        }

        @Override
        public final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvValueFromSecretKeyRef getSecretKeyRef() {
            return this.secretKeyRef;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getConfigMapKeyRef() != null) {
                data.set("configMapKeyRef", om.valueToTree(this.getConfigMapKeyRef()));
            }
            if (this.getFieldRef() != null) {
                data.set("fieldRef", om.valueToTree(this.getFieldRef()));
            }
            if (this.getResourceFieldRef() != null) {
                data.set("resourceFieldRef", om.valueToTree(this.getResourceFieldRef()));
            }
            if (this.getSecretKeyRef() != null) {
                data.set("secretKeyRef", om.valueToTree(this.getSecretKeyRef()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ConfigurationSpecTemplateSpecContainersEnvValueFrom"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecTemplateSpecContainersEnvValueFrom.Jsii$Proxy that = (ConfigurationSpecTemplateSpecContainersEnvValueFrom.Jsii$Proxy) o;

            if (this.configMapKeyRef != null ? !this.configMapKeyRef.equals(that.configMapKeyRef) : that.configMapKeyRef != null) return false;
            if (this.fieldRef != null ? !this.fieldRef.equals(that.fieldRef) : that.fieldRef != null) return false;
            if (this.resourceFieldRef != null ? !this.resourceFieldRef.equals(that.resourceFieldRef) : that.resourceFieldRef != null) return false;
            return this.secretKeyRef != null ? this.secretKeyRef.equals(that.secretKeyRef) : that.secretKeyRef == null;
        }

        @Override
        public final int hashCode() {
            int result = this.configMapKeyRef != null ? this.configMapKeyRef.hashCode() : 0;
            result = 31 * result + (this.fieldRef != null ? this.fieldRef.hashCode() : 0);
            result = 31 * result + (this.resourceFieldRef != null ? this.resourceFieldRef.hashCode() : 0);
            result = 31 * result + (this.secretKeyRef != null ? this.secretKeyRef.hashCode() : 0);
            return result;
        }
    }
}
