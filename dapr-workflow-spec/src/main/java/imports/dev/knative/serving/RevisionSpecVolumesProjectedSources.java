package imports.dev.knative.serving;

/**
 * Projection that may be projected along with other supported volume types.
 * <p>
 * Exactly one of these fields must be set.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.168Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.RevisionSpecVolumesProjectedSources")
@software.amazon.jsii.Jsii.Proxy(RevisionSpecVolumesProjectedSources.Jsii$Proxy.class)
public interface RevisionSpecVolumesProjectedSources extends software.amazon.jsii.JsiiSerializable {

    /**
     * configMap information about the configMap data to project.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesConfigMap getConfigMap() {
        return null;
    }

    /**
     * downwardAPI information about the downwardAPI data to project.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApi getDownwardApi() {
        return null;
    }

    /**
     * secret information about the secret data to project.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesSecret getSecret() {
        return null;
    }

    /**
     * serviceAccountToken is information about the serviceAccountToken data to project.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesServiceAccountToken getServiceAccountToken() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link RevisionSpecVolumesProjectedSources}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link RevisionSpecVolumesProjectedSources}
     */
    public static final class Builder implements software.amazon.jsii.Builder<RevisionSpecVolumesProjectedSources> {
        imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesConfigMap configMap;
        imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApi downwardApi;
        imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesSecret secret;
        imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesServiceAccountToken serviceAccountToken;

        /**
         * Sets the value of {@link RevisionSpecVolumesProjectedSources#getConfigMap}
         * @param configMap configMap information about the configMap data to project.
         * @return {@code this}
         */
        public Builder configMap(imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesConfigMap configMap) {
            this.configMap = configMap;
            return this;
        }

        /**
         * Sets the value of {@link RevisionSpecVolumesProjectedSources#getDownwardApi}
         * @param downwardApi downwardAPI information about the downwardAPI data to project.
         * @return {@code this}
         */
        public Builder downwardApi(imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApi downwardApi) {
            this.downwardApi = downwardApi;
            return this;
        }

        /**
         * Sets the value of {@link RevisionSpecVolumesProjectedSources#getSecret}
         * @param secret secret information about the secret data to project.
         * @return {@code this}
         */
        public Builder secret(imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesSecret secret) {
            this.secret = secret;
            return this;
        }

        /**
         * Sets the value of {@link RevisionSpecVolumesProjectedSources#getServiceAccountToken}
         * @param serviceAccountToken serviceAccountToken is information about the serviceAccountToken data to project.
         * @return {@code this}
         */
        public Builder serviceAccountToken(imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesServiceAccountToken serviceAccountToken) {
            this.serviceAccountToken = serviceAccountToken;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link RevisionSpecVolumesProjectedSources}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public RevisionSpecVolumesProjectedSources build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link RevisionSpecVolumesProjectedSources}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements RevisionSpecVolumesProjectedSources {
        private final imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesConfigMap configMap;
        private final imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApi downwardApi;
        private final imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesSecret secret;
        private final imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesServiceAccountToken serviceAccountToken;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.configMap = software.amazon.jsii.Kernel.get(this, "configMap", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesConfigMap.class));
            this.downwardApi = software.amazon.jsii.Kernel.get(this, "downwardApi", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApi.class));
            this.secret = software.amazon.jsii.Kernel.get(this, "secret", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesSecret.class));
            this.serviceAccountToken = software.amazon.jsii.Kernel.get(this, "serviceAccountToken", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesServiceAccountToken.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.configMap = builder.configMap;
            this.downwardApi = builder.downwardApi;
            this.secret = builder.secret;
            this.serviceAccountToken = builder.serviceAccountToken;
        }

        @Override
        public final imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesConfigMap getConfigMap() {
            return this.configMap;
        }

        @Override
        public final imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApi getDownwardApi() {
            return this.downwardApi;
        }

        @Override
        public final imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesSecret getSecret() {
            return this.secret;
        }

        @Override
        public final imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesServiceAccountToken getServiceAccountToken() {
            return this.serviceAccountToken;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getConfigMap() != null) {
                data.set("configMap", om.valueToTree(this.getConfigMap()));
            }
            if (this.getDownwardApi() != null) {
                data.set("downwardApi", om.valueToTree(this.getDownwardApi()));
            }
            if (this.getSecret() != null) {
                data.set("secret", om.valueToTree(this.getSecret()));
            }
            if (this.getServiceAccountToken() != null) {
                data.set("serviceAccountToken", om.valueToTree(this.getServiceAccountToken()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.RevisionSpecVolumesProjectedSources"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RevisionSpecVolumesProjectedSources.Jsii$Proxy that = (RevisionSpecVolumesProjectedSources.Jsii$Proxy) o;

            if (this.configMap != null ? !this.configMap.equals(that.configMap) : that.configMap != null) return false;
            if (this.downwardApi != null ? !this.downwardApi.equals(that.downwardApi) : that.downwardApi != null) return false;
            if (this.secret != null ? !this.secret.equals(that.secret) : that.secret != null) return false;
            return this.serviceAccountToken != null ? this.serviceAccountToken.equals(that.serviceAccountToken) : that.serviceAccountToken == null;
        }

        @Override
        public final int hashCode() {
            int result = this.configMap != null ? this.configMap.hashCode() : 0;
            result = 31 * result + (this.downwardApi != null ? this.downwardApi.hashCode() : 0);
            result = 31 * result + (this.secret != null ? this.secret.hashCode() : 0);
            result = 31 * result + (this.serviceAccountToken != null ? this.serviceAccountToken.hashCode() : 0);
            return result;
        }
    }
}
