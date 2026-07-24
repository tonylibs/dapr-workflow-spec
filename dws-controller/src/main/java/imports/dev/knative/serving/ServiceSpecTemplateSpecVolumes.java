package imports.dev.knative.serving;

/**
 * Volume represents a named volume in a pod that may be accessed by any container in the pod.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.192Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ServiceSpecTemplateSpecVolumes")
@software.amazon.jsii.Jsii.Proxy(ServiceSpecTemplateSpecVolumes.Jsii$Proxy.class)
public interface ServiceSpecTemplateSpecVolumes extends software.amazon.jsii.JsiiSerializable {

    /**
     * name of the volume.
     * <p>
     * Must be a DNS_LABEL and unique within the pod.
     * More info: https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#names
     */
    @org.jetbrains.annotations.NotNull java.lang.String getName();

    /**
     * configMap represents a configMap that should populate this volume.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesConfigMap getConfigMap() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-volumes-csi.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Object getCsi() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-volumes-emptydir.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Object getEmptyDir() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-volumes-hostpath.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Object getHostPath() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-volumes-image.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Object getImage() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-persistent-volume-claim.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Object getPersistentVolumeClaim() {
        return null;
    }

    /**
     * projected items for all in one resources secrets, configmaps, and downward API.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjected getProjected() {
        return null;
    }

    /**
     * secret represents a secret that should populate this volume.
     * <p>
     * More info: https://kubernetes.io/docs/concepts/storage/volumes#secret
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesSecret getSecret() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ServiceSpecTemplateSpecVolumes}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ServiceSpecTemplateSpecVolumes}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ServiceSpecTemplateSpecVolumes> {
        java.lang.String name;
        imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesConfigMap configMap;
        java.lang.Object csi;
        java.lang.Object emptyDir;
        java.lang.Object hostPath;
        java.lang.Object image;
        java.lang.Object persistentVolumeClaim;
        imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjected projected;
        imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesSecret secret;

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumes#getName}
         * @param name name of the volume. This parameter is required.
         *             Must be a DNS_LABEL and unique within the pod.
         *             More info: https://kubernetes.io/docs/concepts/overview/working-with-objects/names/#names
         * @return {@code this}
         */
        public Builder name(java.lang.String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumes#getConfigMap}
         * @param configMap configMap represents a configMap that should populate this volume.
         * @return {@code this}
         */
        public Builder configMap(imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesConfigMap configMap) {
            this.configMap = configMap;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumes#getCsi}
         * @param csi This is accessible behind a feature flag - kubernetes.podspec-volumes-csi.
         * @return {@code this}
         */
        public Builder csi(java.lang.Object csi) {
            this.csi = csi;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumes#getEmptyDir}
         * @param emptyDir This is accessible behind a feature flag - kubernetes.podspec-volumes-emptydir.
         * @return {@code this}
         */
        public Builder emptyDir(java.lang.Object emptyDir) {
            this.emptyDir = emptyDir;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumes#getHostPath}
         * @param hostPath This is accessible behind a feature flag - kubernetes.podspec-volumes-hostpath.
         * @return {@code this}
         */
        public Builder hostPath(java.lang.Object hostPath) {
            this.hostPath = hostPath;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumes#getImage}
         * @param image This is accessible behind a feature flag - kubernetes.podspec-volumes-image.
         * @return {@code this}
         */
        public Builder image(java.lang.Object image) {
            this.image = image;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumes#getPersistentVolumeClaim}
         * @param persistentVolumeClaim This is accessible behind a feature flag - kubernetes.podspec-persistent-volume-claim.
         * @return {@code this}
         */
        public Builder persistentVolumeClaim(java.lang.Object persistentVolumeClaim) {
            this.persistentVolumeClaim = persistentVolumeClaim;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumes#getProjected}
         * @param projected projected items for all in one resources secrets, configmaps, and downward API.
         * @return {@code this}
         */
        public Builder projected(imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjected projected) {
            this.projected = projected;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecVolumes#getSecret}
         * @param secret secret represents a secret that should populate this volume.
         *               More info: https://kubernetes.io/docs/concepts/storage/volumes#secret
         * @return {@code this}
         */
        public Builder secret(imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesSecret secret) {
            this.secret = secret;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ServiceSpecTemplateSpecVolumes}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ServiceSpecTemplateSpecVolumes build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ServiceSpecTemplateSpecVolumes}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ServiceSpecTemplateSpecVolumes {
        private final java.lang.String name;
        private final imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesConfigMap configMap;
        private final java.lang.Object csi;
        private final java.lang.Object emptyDir;
        private final java.lang.Object hostPath;
        private final java.lang.Object image;
        private final java.lang.Object persistentVolumeClaim;
        private final imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjected projected;
        private final imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesSecret secret;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.configMap = software.amazon.jsii.Kernel.get(this, "configMap", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesConfigMap.class));
            this.csi = software.amazon.jsii.Kernel.get(this, "csi", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
            this.emptyDir = software.amazon.jsii.Kernel.get(this, "emptyDir", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
            this.hostPath = software.amazon.jsii.Kernel.get(this, "hostPath", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
            this.image = software.amazon.jsii.Kernel.get(this, "image", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
            this.persistentVolumeClaim = software.amazon.jsii.Kernel.get(this, "persistentVolumeClaim", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
            this.projected = software.amazon.jsii.Kernel.get(this, "projected", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjected.class));
            this.secret = software.amazon.jsii.Kernel.get(this, "secret", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesSecret.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.name = java.util.Objects.requireNonNull(builder.name, "name is required");
            this.configMap = builder.configMap;
            this.csi = builder.csi;
            this.emptyDir = builder.emptyDir;
            this.hostPath = builder.hostPath;
            this.image = builder.image;
            this.persistentVolumeClaim = builder.persistentVolumeClaim;
            this.projected = builder.projected;
            this.secret = builder.secret;
        }

        @Override
        public final java.lang.String getName() {
            return this.name;
        }

        @Override
        public final imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesConfigMap getConfigMap() {
            return this.configMap;
        }

        @Override
        public final java.lang.Object getCsi() {
            return this.csi;
        }

        @Override
        public final java.lang.Object getEmptyDir() {
            return this.emptyDir;
        }

        @Override
        public final java.lang.Object getHostPath() {
            return this.hostPath;
        }

        @Override
        public final java.lang.Object getImage() {
            return this.image;
        }

        @Override
        public final java.lang.Object getPersistentVolumeClaim() {
            return this.persistentVolumeClaim;
        }

        @Override
        public final imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesProjected getProjected() {
            return this.projected;
        }

        @Override
        public final imports.dev.knative.serving.ServiceSpecTemplateSpecVolumesSecret getSecret() {
            return this.secret;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("name", om.valueToTree(this.getName()));
            if (this.getConfigMap() != null) {
                data.set("configMap", om.valueToTree(this.getConfigMap()));
            }
            if (this.getCsi() != null) {
                data.set("csi", om.valueToTree(this.getCsi()));
            }
            if (this.getEmptyDir() != null) {
                data.set("emptyDir", om.valueToTree(this.getEmptyDir()));
            }
            if (this.getHostPath() != null) {
                data.set("hostPath", om.valueToTree(this.getHostPath()));
            }
            if (this.getImage() != null) {
                data.set("image", om.valueToTree(this.getImage()));
            }
            if (this.getPersistentVolumeClaim() != null) {
                data.set("persistentVolumeClaim", om.valueToTree(this.getPersistentVolumeClaim()));
            }
            if (this.getProjected() != null) {
                data.set("projected", om.valueToTree(this.getProjected()));
            }
            if (this.getSecret() != null) {
                data.set("secret", om.valueToTree(this.getSecret()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ServiceSpecTemplateSpecVolumes"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ServiceSpecTemplateSpecVolumes.Jsii$Proxy that = (ServiceSpecTemplateSpecVolumes.Jsii$Proxy) o;

            if (!name.equals(that.name)) return false;
            if (this.configMap != null ? !this.configMap.equals(that.configMap) : that.configMap != null) return false;
            if (this.csi != null ? !this.csi.equals(that.csi) : that.csi != null) return false;
            if (this.emptyDir != null ? !this.emptyDir.equals(that.emptyDir) : that.emptyDir != null) return false;
            if (this.hostPath != null ? !this.hostPath.equals(that.hostPath) : that.hostPath != null) return false;
            if (this.image != null ? !this.image.equals(that.image) : that.image != null) return false;
            if (this.persistentVolumeClaim != null ? !this.persistentVolumeClaim.equals(that.persistentVolumeClaim) : that.persistentVolumeClaim != null) return false;
            if (this.projected != null ? !this.projected.equals(that.projected) : that.projected != null) return false;
            return this.secret != null ? this.secret.equals(that.secret) : that.secret == null;
        }

        @Override
        public final int hashCode() {
            int result = this.name.hashCode();
            result = 31 * result + (this.configMap != null ? this.configMap.hashCode() : 0);
            result = 31 * result + (this.csi != null ? this.csi.hashCode() : 0);
            result = 31 * result + (this.emptyDir != null ? this.emptyDir.hashCode() : 0);
            result = 31 * result + (this.hostPath != null ? this.hostPath.hashCode() : 0);
            result = 31 * result + (this.image != null ? this.image.hashCode() : 0);
            result = 31 * result + (this.persistentVolumeClaim != null ? this.persistentVolumeClaim.hashCode() : 0);
            result = 31 * result + (this.projected != null ? this.projected.hashCode() : 0);
            result = 31 * result + (this.secret != null ? this.secret.hashCode() : 0);
            return result;
        }
    }
}
