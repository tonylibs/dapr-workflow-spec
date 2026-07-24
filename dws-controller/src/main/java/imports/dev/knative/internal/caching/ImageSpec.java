package imports.dev.knative.internal.caching;

/**
 * Spec holds the desired state of the Image (from the client).
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:07.187Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.caching.$Module.class, fqn = "devknativeinternalcaching.ImageSpec")
@software.amazon.jsii.Jsii.Proxy(ImageSpec.Jsii$Proxy.class)
public interface ImageSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     * Image is the name of the container image url to cache across the cluster.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getImage();

    /**
     * ImagePullSecrets contains the names of the Kubernetes Secrets containing login information used by the Pods which will run this container.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.internal.caching.ImageSpecImagePullSecrets> getImagePullSecrets() {
        return null;
    }

    /**
     * ServiceAccountName is the name of the Kubernetes ServiceAccount as which the Pods will run this container.
     * <p>
     * This is potentially used to authenticate the image pull
     * if the service account has attached pull secrets.  For more information:
     * https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/#add-imagepullsecrets-to-a-service-account
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getServiceAccountName() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ImageSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ImageSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ImageSpec> {
        java.lang.String image;
        java.util.List<imports.dev.knative.internal.caching.ImageSpecImagePullSecrets> imagePullSecrets;
        java.lang.String serviceAccountName;

        /**
         * Sets the value of {@link ImageSpec#getImage}
         * @param image Image is the name of the container image url to cache across the cluster. This parameter is required.
         * @return {@code this}
         */
        public Builder image(java.lang.String image) {
            this.image = image;
            return this;
        }

        /**
         * Sets the value of {@link ImageSpec#getImagePullSecrets}
         * @param imagePullSecrets ImagePullSecrets contains the names of the Kubernetes Secrets containing login information used by the Pods which will run this container.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder imagePullSecrets(java.util.List<? extends imports.dev.knative.internal.caching.ImageSpecImagePullSecrets> imagePullSecrets) {
            this.imagePullSecrets = (java.util.List<imports.dev.knative.internal.caching.ImageSpecImagePullSecrets>)imagePullSecrets;
            return this;
        }

        /**
         * Sets the value of {@link ImageSpec#getServiceAccountName}
         * @param serviceAccountName ServiceAccountName is the name of the Kubernetes ServiceAccount as which the Pods will run this container.
         *                           This is potentially used to authenticate the image pull
         *                           if the service account has attached pull secrets.  For more information:
         *                           https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/#add-imagepullsecrets-to-a-service-account
         * @return {@code this}
         */
        public Builder serviceAccountName(java.lang.String serviceAccountName) {
            this.serviceAccountName = serviceAccountName;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ImageSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ImageSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ImageSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ImageSpec {
        private final java.lang.String image;
        private final java.util.List<imports.dev.knative.internal.caching.ImageSpecImagePullSecrets> imagePullSecrets;
        private final java.lang.String serviceAccountName;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.image = software.amazon.jsii.Kernel.get(this, "image", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.imagePullSecrets = software.amazon.jsii.Kernel.get(this, "imagePullSecrets", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.internal.caching.ImageSpecImagePullSecrets.class)));
            this.serviceAccountName = software.amazon.jsii.Kernel.get(this, "serviceAccountName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.image = java.util.Objects.requireNonNull(builder.image, "image is required");
            this.imagePullSecrets = (java.util.List<imports.dev.knative.internal.caching.ImageSpecImagePullSecrets>)builder.imagePullSecrets;
            this.serviceAccountName = builder.serviceAccountName;
        }

        @Override
        public final java.lang.String getImage() {
            return this.image;
        }

        @Override
        public final java.util.List<imports.dev.knative.internal.caching.ImageSpecImagePullSecrets> getImagePullSecrets() {
            return this.imagePullSecrets;
        }

        @Override
        public final java.lang.String getServiceAccountName() {
            return this.serviceAccountName;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("image", om.valueToTree(this.getImage()));
            if (this.getImagePullSecrets() != null) {
                data.set("imagePullSecrets", om.valueToTree(this.getImagePullSecrets()));
            }
            if (this.getServiceAccountName() != null) {
                data.set("serviceAccountName", om.valueToTree(this.getServiceAccountName()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeinternalcaching.ImageSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ImageSpec.Jsii$Proxy that = (ImageSpec.Jsii$Proxy) o;

            if (!image.equals(that.image)) return false;
            if (this.imagePullSecrets != null ? !this.imagePullSecrets.equals(that.imagePullSecrets) : that.imagePullSecrets != null) return false;
            return this.serviceAccountName != null ? this.serviceAccountName.equals(that.serviceAccountName) : that.serviceAccountName == null;
        }

        @Override
        public final int hashCode() {
            int result = this.image.hashCode();
            result = 31 * result + (this.imagePullSecrets != null ? this.imagePullSecrets.hashCode() : 0);
            result = 31 * result + (this.serviceAccountName != null ? this.serviceAccountName.hashCode() : 0);
            return result;
        }
    }
}
