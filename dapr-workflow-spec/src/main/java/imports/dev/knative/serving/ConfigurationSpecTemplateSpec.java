package imports.dev.knative.serving;

/**
 * RevisionSpec holds the desired state of the Revision (from the client).
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.098Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ConfigurationSpecTemplateSpec")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecTemplateSpec.Jsii$Proxy.class)
public interface ConfigurationSpecTemplateSpec extends software.amazon.jsii.JsiiSerializable {

    /**
     * List of containers belonging to the pod.
     * <p>
     * Containers cannot currently be added or removed.
     * There must be at least one container in a Pod.
     * Cannot be updated.
     */
    @org.jetbrains.annotations.NotNull java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainers> getContainers();

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-affinity.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Object getAffinity() {
        return null;
    }

    /**
     * AutomountServiceAccountToken indicates whether a service account token should be automatically mounted.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getAutomountServiceAccountToken() {
        return null;
    }

    /**
     * ContainerConcurrency specifies the maximum allowed in-flight (concurrent) requests per container of the Revision.
     * <p>
     * Defaults to <code>0</code> which means
     * concurrency to the application is not limited, and the system decides the
     * target concurrency for the autoscaler.
     * <p>
     * Default: 0` which means
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getContainerConcurrency() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-dnsconfig.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Object getDnsConfig() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-dnspolicy.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getDnsPolicy() {
        return null;
    }

    /**
     * EnableServiceLinks indicates whether information aboutservices should be injected into pod's environment variables, matching the syntax of Docker links.
     * <p>
     * Optional: Knative defaults this to false.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getEnableServiceLinks() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-hostaliases.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.Object> getHostAliases() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-hostipc.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getHostIpc() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-hostnetwork.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getHostNetwork() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-hostpid.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getHostPid() {
        return null;
    }

    /**
     * IdleTimeoutSeconds is the maximum duration in seconds a request will be allowed to stay open while not receiving any bytes from the user's application.
     * <p>
     * If
     * unspecified, a system default will be provided.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getIdleTimeoutSeconds() {
        return null;
    }

    /**
     * ImagePullSecrets is an optional list of references to secrets in the same namespace to use for pulling any of the images used by this PodSpec.
     * <p>
     * If specified, these secrets will be passed to individual puller implementations for them to use.
     * More info: https://kubernetes.io/docs/concepts/containers/images#specifying-imagepullsecrets-on-a-pod
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecImagePullSecrets> getImagePullSecrets() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-init-containers.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.Object> getInitContainers() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-nodeselector.
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, java.lang.String> getNodeSelector() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-priorityclassname.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getPriorityClassName() {
        return null;
    }

    /**
     * ResponseStartTimeoutSeconds is the maximum duration in seconds that the request routing layer will wait for a request delivered to a container to begin sending any network traffic.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getResponseStartTimeoutSeconds() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-runtimeclassname.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getRuntimeClassName() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-schedulername.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getSchedulerName() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-securitycontext.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Object getSecurityContext() {
        return null;
    }

    /**
     * ServiceAccountName is the name of the ServiceAccount to use to run this pod.
     * <p>
     * More info: https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getServiceAccountName() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-shareprocessnamespace.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getShareProcessNamespace() {
        return null;
    }

    /**
     * TimeoutSeconds is the maximum duration in seconds that the request instance is allowed to respond to a request.
     * <p>
     * If unspecified, a system default will
     * be provided.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getTimeoutSeconds() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-tolerations.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.Object> getTolerations() {
        return null;
    }

    /**
     * This is accessible behind a feature flag - kubernetes.podspec-topologyspreadconstraints.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.Object> getTopologySpreadConstraints() {
        return null;
    }

    /**
     * List of volumes that can be mounted by containers belonging to the pod.
     * <p>
     * More info: https://kubernetes.io/docs/concepts/storage/volumes
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumes> getVolumes() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecTemplateSpec}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecTemplateSpec}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecTemplateSpec> {
        java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainers> containers;
        java.lang.Object affinity;
        java.lang.Boolean automountServiceAccountToken;
        java.lang.Number containerConcurrency;
        java.lang.Object dnsConfig;
        java.lang.String dnsPolicy;
        java.lang.Boolean enableServiceLinks;
        java.util.List<java.lang.Object> hostAliases;
        java.lang.Boolean hostIpc;
        java.lang.Boolean hostNetwork;
        java.lang.Boolean hostPid;
        java.lang.Number idleTimeoutSeconds;
        java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecImagePullSecrets> imagePullSecrets;
        java.util.List<java.lang.Object> initContainers;
        java.util.Map<java.lang.String, java.lang.String> nodeSelector;
        java.lang.String priorityClassName;
        java.lang.Number responseStartTimeoutSeconds;
        java.lang.String runtimeClassName;
        java.lang.String schedulerName;
        java.lang.Object securityContext;
        java.lang.String serviceAccountName;
        java.lang.Boolean shareProcessNamespace;
        java.lang.Number timeoutSeconds;
        java.util.List<java.lang.Object> tolerations;
        java.util.List<java.lang.Object> topologySpreadConstraints;
        java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumes> volumes;

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getContainers}
         * @param containers List of containers belonging to the pod. This parameter is required.
         *                   Containers cannot currently be added or removed.
         *                   There must be at least one container in a Pod.
         *                   Cannot be updated.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder containers(java.util.List<? extends imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainers> containers) {
            this.containers = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainers>)containers;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getAffinity}
         * @param affinity This is accessible behind a feature flag - kubernetes.podspec-affinity.
         * @return {@code this}
         */
        public Builder affinity(java.lang.Object affinity) {
            this.affinity = affinity;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getAutomountServiceAccountToken}
         * @param automountServiceAccountToken AutomountServiceAccountToken indicates whether a service account token should be automatically mounted.
         * @return {@code this}
         */
        public Builder automountServiceAccountToken(java.lang.Boolean automountServiceAccountToken) {
            this.automountServiceAccountToken = automountServiceAccountToken;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getContainerConcurrency}
         * @param containerConcurrency ContainerConcurrency specifies the maximum allowed in-flight (concurrent) requests per container of the Revision.
         *                             Defaults to <code>0</code> which means
         *                             concurrency to the application is not limited, and the system decides the
         *                             target concurrency for the autoscaler.
         * @return {@code this}
         */
        public Builder containerConcurrency(java.lang.Number containerConcurrency) {
            this.containerConcurrency = containerConcurrency;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getDnsConfig}
         * @param dnsConfig This is accessible behind a feature flag - kubernetes.podspec-dnsconfig.
         * @return {@code this}
         */
        public Builder dnsConfig(java.lang.Object dnsConfig) {
            this.dnsConfig = dnsConfig;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getDnsPolicy}
         * @param dnsPolicy This is accessible behind a feature flag - kubernetes.podspec-dnspolicy.
         * @return {@code this}
         */
        public Builder dnsPolicy(java.lang.String dnsPolicy) {
            this.dnsPolicy = dnsPolicy;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getEnableServiceLinks}
         * @param enableServiceLinks EnableServiceLinks indicates whether information aboutservices should be injected into pod's environment variables, matching the syntax of Docker links.
         *                           Optional: Knative defaults this to false.
         * @return {@code this}
         */
        public Builder enableServiceLinks(java.lang.Boolean enableServiceLinks) {
            this.enableServiceLinks = enableServiceLinks;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getHostAliases}
         * @param hostAliases This is accessible behind a feature flag - kubernetes.podspec-hostaliases.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder hostAliases(java.util.List<? extends java.lang.Object> hostAliases) {
            this.hostAliases = (java.util.List<java.lang.Object>)hostAliases;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getHostIpc}
         * @param hostIpc This is accessible behind a feature flag - kubernetes.podspec-hostipc.
         * @return {@code this}
         */
        public Builder hostIpc(java.lang.Boolean hostIpc) {
            this.hostIpc = hostIpc;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getHostNetwork}
         * @param hostNetwork This is accessible behind a feature flag - kubernetes.podspec-hostnetwork.
         * @return {@code this}
         */
        public Builder hostNetwork(java.lang.Boolean hostNetwork) {
            this.hostNetwork = hostNetwork;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getHostPid}
         * @param hostPid This is accessible behind a feature flag - kubernetes.podspec-hostpid.
         * @return {@code this}
         */
        public Builder hostPid(java.lang.Boolean hostPid) {
            this.hostPid = hostPid;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getIdleTimeoutSeconds}
         * @param idleTimeoutSeconds IdleTimeoutSeconds is the maximum duration in seconds a request will be allowed to stay open while not receiving any bytes from the user's application.
         *                           If
         *                           unspecified, a system default will be provided.
         * @return {@code this}
         */
        public Builder idleTimeoutSeconds(java.lang.Number idleTimeoutSeconds) {
            this.idleTimeoutSeconds = idleTimeoutSeconds;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getImagePullSecrets}
         * @param imagePullSecrets ImagePullSecrets is an optional list of references to secrets in the same namespace to use for pulling any of the images used by this PodSpec.
         *                         If specified, these secrets will be passed to individual puller implementations for them to use.
         *                         More info: https://kubernetes.io/docs/concepts/containers/images#specifying-imagepullsecrets-on-a-pod
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder imagePullSecrets(java.util.List<? extends imports.dev.knative.serving.ConfigurationSpecTemplateSpecImagePullSecrets> imagePullSecrets) {
            this.imagePullSecrets = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecImagePullSecrets>)imagePullSecrets;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getInitContainers}
         * @param initContainers This is accessible behind a feature flag - kubernetes.podspec-init-containers.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder initContainers(java.util.List<? extends java.lang.Object> initContainers) {
            this.initContainers = (java.util.List<java.lang.Object>)initContainers;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getNodeSelector}
         * @param nodeSelector This is accessible behind a feature flag - kubernetes.podspec-nodeselector.
         * @return {@code this}
         */
        public Builder nodeSelector(java.util.Map<java.lang.String, java.lang.String> nodeSelector) {
            this.nodeSelector = nodeSelector;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getPriorityClassName}
         * @param priorityClassName This is accessible behind a feature flag - kubernetes.podspec-priorityclassname.
         * @return {@code this}
         */
        public Builder priorityClassName(java.lang.String priorityClassName) {
            this.priorityClassName = priorityClassName;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getResponseStartTimeoutSeconds}
         * @param responseStartTimeoutSeconds ResponseStartTimeoutSeconds is the maximum duration in seconds that the request routing layer will wait for a request delivered to a container to begin sending any network traffic.
         * @return {@code this}
         */
        public Builder responseStartTimeoutSeconds(java.lang.Number responseStartTimeoutSeconds) {
            this.responseStartTimeoutSeconds = responseStartTimeoutSeconds;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getRuntimeClassName}
         * @param runtimeClassName This is accessible behind a feature flag - kubernetes.podspec-runtimeclassname.
         * @return {@code this}
         */
        public Builder runtimeClassName(java.lang.String runtimeClassName) {
            this.runtimeClassName = runtimeClassName;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getSchedulerName}
         * @param schedulerName This is accessible behind a feature flag - kubernetes.podspec-schedulername.
         * @return {@code this}
         */
        public Builder schedulerName(java.lang.String schedulerName) {
            this.schedulerName = schedulerName;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getSecurityContext}
         * @param securityContext This is accessible behind a feature flag - kubernetes.podspec-securitycontext.
         * @return {@code this}
         */
        public Builder securityContext(java.lang.Object securityContext) {
            this.securityContext = securityContext;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getServiceAccountName}
         * @param serviceAccountName ServiceAccountName is the name of the ServiceAccount to use to run this pod.
         *                           More info: https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/
         * @return {@code this}
         */
        public Builder serviceAccountName(java.lang.String serviceAccountName) {
            this.serviceAccountName = serviceAccountName;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getShareProcessNamespace}
         * @param shareProcessNamespace This is accessible behind a feature flag - kubernetes.podspec-shareprocessnamespace.
         * @return {@code this}
         */
        public Builder shareProcessNamespace(java.lang.Boolean shareProcessNamespace) {
            this.shareProcessNamespace = shareProcessNamespace;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getTimeoutSeconds}
         * @param timeoutSeconds TimeoutSeconds is the maximum duration in seconds that the request instance is allowed to respond to a request.
         *                       If unspecified, a system default will
         *                       be provided.
         * @return {@code this}
         */
        public Builder timeoutSeconds(java.lang.Number timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getTolerations}
         * @param tolerations This is accessible behind a feature flag - kubernetes.podspec-tolerations.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder tolerations(java.util.List<? extends java.lang.Object> tolerations) {
            this.tolerations = (java.util.List<java.lang.Object>)tolerations;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getTopologySpreadConstraints}
         * @param topologySpreadConstraints This is accessible behind a feature flag - kubernetes.podspec-topologyspreadconstraints.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder topologySpreadConstraints(java.util.List<? extends java.lang.Object> topologySpreadConstraints) {
            this.topologySpreadConstraints = (java.util.List<java.lang.Object>)topologySpreadConstraints;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpec#getVolumes}
         * @param volumes List of volumes that can be mounted by containers belonging to the pod.
         *                More info: https://kubernetes.io/docs/concepts/storage/volumes
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder volumes(java.util.List<? extends imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumes> volumes) {
            this.volumes = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumes>)volumes;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecTemplateSpec}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecTemplateSpec build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecTemplateSpec}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecTemplateSpec {
        private final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainers> containers;
        private final java.lang.Object affinity;
        private final java.lang.Boolean automountServiceAccountToken;
        private final java.lang.Number containerConcurrency;
        private final java.lang.Object dnsConfig;
        private final java.lang.String dnsPolicy;
        private final java.lang.Boolean enableServiceLinks;
        private final java.util.List<java.lang.Object> hostAliases;
        private final java.lang.Boolean hostIpc;
        private final java.lang.Boolean hostNetwork;
        private final java.lang.Boolean hostPid;
        private final java.lang.Number idleTimeoutSeconds;
        private final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecImagePullSecrets> imagePullSecrets;
        private final java.util.List<java.lang.Object> initContainers;
        private final java.util.Map<java.lang.String, java.lang.String> nodeSelector;
        private final java.lang.String priorityClassName;
        private final java.lang.Number responseStartTimeoutSeconds;
        private final java.lang.String runtimeClassName;
        private final java.lang.String schedulerName;
        private final java.lang.Object securityContext;
        private final java.lang.String serviceAccountName;
        private final java.lang.Boolean shareProcessNamespace;
        private final java.lang.Number timeoutSeconds;
        private final java.util.List<java.lang.Object> tolerations;
        private final java.util.List<java.lang.Object> topologySpreadConstraints;
        private final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumes> volumes;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.containers = software.amazon.jsii.Kernel.get(this, "containers", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainers.class)));
            this.affinity = software.amazon.jsii.Kernel.get(this, "affinity", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
            this.automountServiceAccountToken = software.amazon.jsii.Kernel.get(this, "automountServiceAccountToken", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.containerConcurrency = software.amazon.jsii.Kernel.get(this, "containerConcurrency", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.dnsConfig = software.amazon.jsii.Kernel.get(this, "dnsConfig", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
            this.dnsPolicy = software.amazon.jsii.Kernel.get(this, "dnsPolicy", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.enableServiceLinks = software.amazon.jsii.Kernel.get(this, "enableServiceLinks", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.hostAliases = software.amazon.jsii.Kernel.get(this, "hostAliases", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.Object.class)));
            this.hostIpc = software.amazon.jsii.Kernel.get(this, "hostIpc", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.hostNetwork = software.amazon.jsii.Kernel.get(this, "hostNetwork", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.hostPid = software.amazon.jsii.Kernel.get(this, "hostPid", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.idleTimeoutSeconds = software.amazon.jsii.Kernel.get(this, "idleTimeoutSeconds", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.imagePullSecrets = software.amazon.jsii.Kernel.get(this, "imagePullSecrets", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecImagePullSecrets.class)));
            this.initContainers = software.amazon.jsii.Kernel.get(this, "initContainers", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.Object.class)));
            this.nodeSelector = software.amazon.jsii.Kernel.get(this, "nodeSelector", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.priorityClassName = software.amazon.jsii.Kernel.get(this, "priorityClassName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.responseStartTimeoutSeconds = software.amazon.jsii.Kernel.get(this, "responseStartTimeoutSeconds", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.runtimeClassName = software.amazon.jsii.Kernel.get(this, "runtimeClassName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.schedulerName = software.amazon.jsii.Kernel.get(this, "schedulerName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.securityContext = software.amazon.jsii.Kernel.get(this, "securityContext", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
            this.serviceAccountName = software.amazon.jsii.Kernel.get(this, "serviceAccountName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.shareProcessNamespace = software.amazon.jsii.Kernel.get(this, "shareProcessNamespace", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.timeoutSeconds = software.amazon.jsii.Kernel.get(this, "timeoutSeconds", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.tolerations = software.amazon.jsii.Kernel.get(this, "tolerations", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.Object.class)));
            this.topologySpreadConstraints = software.amazon.jsii.Kernel.get(this, "topologySpreadConstraints", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.Object.class)));
            this.volumes = software.amazon.jsii.Kernel.get(this, "volumes", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumes.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.containers = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainers>)java.util.Objects.requireNonNull(builder.containers, "containers is required");
            this.affinity = builder.affinity;
            this.automountServiceAccountToken = builder.automountServiceAccountToken;
            this.containerConcurrency = builder.containerConcurrency;
            this.dnsConfig = builder.dnsConfig;
            this.dnsPolicy = builder.dnsPolicy;
            this.enableServiceLinks = builder.enableServiceLinks;
            this.hostAliases = (java.util.List<java.lang.Object>)builder.hostAliases;
            this.hostIpc = builder.hostIpc;
            this.hostNetwork = builder.hostNetwork;
            this.hostPid = builder.hostPid;
            this.idleTimeoutSeconds = builder.idleTimeoutSeconds;
            this.imagePullSecrets = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecImagePullSecrets>)builder.imagePullSecrets;
            this.initContainers = (java.util.List<java.lang.Object>)builder.initContainers;
            this.nodeSelector = builder.nodeSelector;
            this.priorityClassName = builder.priorityClassName;
            this.responseStartTimeoutSeconds = builder.responseStartTimeoutSeconds;
            this.runtimeClassName = builder.runtimeClassName;
            this.schedulerName = builder.schedulerName;
            this.securityContext = builder.securityContext;
            this.serviceAccountName = builder.serviceAccountName;
            this.shareProcessNamespace = builder.shareProcessNamespace;
            this.timeoutSeconds = builder.timeoutSeconds;
            this.tolerations = (java.util.List<java.lang.Object>)builder.tolerations;
            this.topologySpreadConstraints = (java.util.List<java.lang.Object>)builder.topologySpreadConstraints;
            this.volumes = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumes>)builder.volumes;
        }

        @Override
        public final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainers> getContainers() {
            return this.containers;
        }

        @Override
        public final java.lang.Object getAffinity() {
            return this.affinity;
        }

        @Override
        public final java.lang.Boolean getAutomountServiceAccountToken() {
            return this.automountServiceAccountToken;
        }

        @Override
        public final java.lang.Number getContainerConcurrency() {
            return this.containerConcurrency;
        }

        @Override
        public final java.lang.Object getDnsConfig() {
            return this.dnsConfig;
        }

        @Override
        public final java.lang.String getDnsPolicy() {
            return this.dnsPolicy;
        }

        @Override
        public final java.lang.Boolean getEnableServiceLinks() {
            return this.enableServiceLinks;
        }

        @Override
        public final java.util.List<java.lang.Object> getHostAliases() {
            return this.hostAliases;
        }

        @Override
        public final java.lang.Boolean getHostIpc() {
            return this.hostIpc;
        }

        @Override
        public final java.lang.Boolean getHostNetwork() {
            return this.hostNetwork;
        }

        @Override
        public final java.lang.Boolean getHostPid() {
            return this.hostPid;
        }

        @Override
        public final java.lang.Number getIdleTimeoutSeconds() {
            return this.idleTimeoutSeconds;
        }

        @Override
        public final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecImagePullSecrets> getImagePullSecrets() {
            return this.imagePullSecrets;
        }

        @Override
        public final java.util.List<java.lang.Object> getInitContainers() {
            return this.initContainers;
        }

        @Override
        public final java.util.Map<java.lang.String, java.lang.String> getNodeSelector() {
            return this.nodeSelector;
        }

        @Override
        public final java.lang.String getPriorityClassName() {
            return this.priorityClassName;
        }

        @Override
        public final java.lang.Number getResponseStartTimeoutSeconds() {
            return this.responseStartTimeoutSeconds;
        }

        @Override
        public final java.lang.String getRuntimeClassName() {
            return this.runtimeClassName;
        }

        @Override
        public final java.lang.String getSchedulerName() {
            return this.schedulerName;
        }

        @Override
        public final java.lang.Object getSecurityContext() {
            return this.securityContext;
        }

        @Override
        public final java.lang.String getServiceAccountName() {
            return this.serviceAccountName;
        }

        @Override
        public final java.lang.Boolean getShareProcessNamespace() {
            return this.shareProcessNamespace;
        }

        @Override
        public final java.lang.Number getTimeoutSeconds() {
            return this.timeoutSeconds;
        }

        @Override
        public final java.util.List<java.lang.Object> getTolerations() {
            return this.tolerations;
        }

        @Override
        public final java.util.List<java.lang.Object> getTopologySpreadConstraints() {
            return this.topologySpreadConstraints;
        }

        @Override
        public final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumes> getVolumes() {
            return this.volumes;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("containers", om.valueToTree(this.getContainers()));
            if (this.getAffinity() != null) {
                data.set("affinity", om.valueToTree(this.getAffinity()));
            }
            if (this.getAutomountServiceAccountToken() != null) {
                data.set("automountServiceAccountToken", om.valueToTree(this.getAutomountServiceAccountToken()));
            }
            if (this.getContainerConcurrency() != null) {
                data.set("containerConcurrency", om.valueToTree(this.getContainerConcurrency()));
            }
            if (this.getDnsConfig() != null) {
                data.set("dnsConfig", om.valueToTree(this.getDnsConfig()));
            }
            if (this.getDnsPolicy() != null) {
                data.set("dnsPolicy", om.valueToTree(this.getDnsPolicy()));
            }
            if (this.getEnableServiceLinks() != null) {
                data.set("enableServiceLinks", om.valueToTree(this.getEnableServiceLinks()));
            }
            if (this.getHostAliases() != null) {
                data.set("hostAliases", om.valueToTree(this.getHostAliases()));
            }
            if (this.getHostIpc() != null) {
                data.set("hostIpc", om.valueToTree(this.getHostIpc()));
            }
            if (this.getHostNetwork() != null) {
                data.set("hostNetwork", om.valueToTree(this.getHostNetwork()));
            }
            if (this.getHostPid() != null) {
                data.set("hostPid", om.valueToTree(this.getHostPid()));
            }
            if (this.getIdleTimeoutSeconds() != null) {
                data.set("idleTimeoutSeconds", om.valueToTree(this.getIdleTimeoutSeconds()));
            }
            if (this.getImagePullSecrets() != null) {
                data.set("imagePullSecrets", om.valueToTree(this.getImagePullSecrets()));
            }
            if (this.getInitContainers() != null) {
                data.set("initContainers", om.valueToTree(this.getInitContainers()));
            }
            if (this.getNodeSelector() != null) {
                data.set("nodeSelector", om.valueToTree(this.getNodeSelector()));
            }
            if (this.getPriorityClassName() != null) {
                data.set("priorityClassName", om.valueToTree(this.getPriorityClassName()));
            }
            if (this.getResponseStartTimeoutSeconds() != null) {
                data.set("responseStartTimeoutSeconds", om.valueToTree(this.getResponseStartTimeoutSeconds()));
            }
            if (this.getRuntimeClassName() != null) {
                data.set("runtimeClassName", om.valueToTree(this.getRuntimeClassName()));
            }
            if (this.getSchedulerName() != null) {
                data.set("schedulerName", om.valueToTree(this.getSchedulerName()));
            }
            if (this.getSecurityContext() != null) {
                data.set("securityContext", om.valueToTree(this.getSecurityContext()));
            }
            if (this.getServiceAccountName() != null) {
                data.set("serviceAccountName", om.valueToTree(this.getServiceAccountName()));
            }
            if (this.getShareProcessNamespace() != null) {
                data.set("shareProcessNamespace", om.valueToTree(this.getShareProcessNamespace()));
            }
            if (this.getTimeoutSeconds() != null) {
                data.set("timeoutSeconds", om.valueToTree(this.getTimeoutSeconds()));
            }
            if (this.getTolerations() != null) {
                data.set("tolerations", om.valueToTree(this.getTolerations()));
            }
            if (this.getTopologySpreadConstraints() != null) {
                data.set("topologySpreadConstraints", om.valueToTree(this.getTopologySpreadConstraints()));
            }
            if (this.getVolumes() != null) {
                data.set("volumes", om.valueToTree(this.getVolumes()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ConfigurationSpecTemplateSpec"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecTemplateSpec.Jsii$Proxy that = (ConfigurationSpecTemplateSpec.Jsii$Proxy) o;

            if (!containers.equals(that.containers)) return false;
            if (this.affinity != null ? !this.affinity.equals(that.affinity) : that.affinity != null) return false;
            if (this.automountServiceAccountToken != null ? !this.automountServiceAccountToken.equals(that.automountServiceAccountToken) : that.automountServiceAccountToken != null) return false;
            if (this.containerConcurrency != null ? !this.containerConcurrency.equals(that.containerConcurrency) : that.containerConcurrency != null) return false;
            if (this.dnsConfig != null ? !this.dnsConfig.equals(that.dnsConfig) : that.dnsConfig != null) return false;
            if (this.dnsPolicy != null ? !this.dnsPolicy.equals(that.dnsPolicy) : that.dnsPolicy != null) return false;
            if (this.enableServiceLinks != null ? !this.enableServiceLinks.equals(that.enableServiceLinks) : that.enableServiceLinks != null) return false;
            if (this.hostAliases != null ? !this.hostAliases.equals(that.hostAliases) : that.hostAliases != null) return false;
            if (this.hostIpc != null ? !this.hostIpc.equals(that.hostIpc) : that.hostIpc != null) return false;
            if (this.hostNetwork != null ? !this.hostNetwork.equals(that.hostNetwork) : that.hostNetwork != null) return false;
            if (this.hostPid != null ? !this.hostPid.equals(that.hostPid) : that.hostPid != null) return false;
            if (this.idleTimeoutSeconds != null ? !this.idleTimeoutSeconds.equals(that.idleTimeoutSeconds) : that.idleTimeoutSeconds != null) return false;
            if (this.imagePullSecrets != null ? !this.imagePullSecrets.equals(that.imagePullSecrets) : that.imagePullSecrets != null) return false;
            if (this.initContainers != null ? !this.initContainers.equals(that.initContainers) : that.initContainers != null) return false;
            if (this.nodeSelector != null ? !this.nodeSelector.equals(that.nodeSelector) : that.nodeSelector != null) return false;
            if (this.priorityClassName != null ? !this.priorityClassName.equals(that.priorityClassName) : that.priorityClassName != null) return false;
            if (this.responseStartTimeoutSeconds != null ? !this.responseStartTimeoutSeconds.equals(that.responseStartTimeoutSeconds) : that.responseStartTimeoutSeconds != null) return false;
            if (this.runtimeClassName != null ? !this.runtimeClassName.equals(that.runtimeClassName) : that.runtimeClassName != null) return false;
            if (this.schedulerName != null ? !this.schedulerName.equals(that.schedulerName) : that.schedulerName != null) return false;
            if (this.securityContext != null ? !this.securityContext.equals(that.securityContext) : that.securityContext != null) return false;
            if (this.serviceAccountName != null ? !this.serviceAccountName.equals(that.serviceAccountName) : that.serviceAccountName != null) return false;
            if (this.shareProcessNamespace != null ? !this.shareProcessNamespace.equals(that.shareProcessNamespace) : that.shareProcessNamespace != null) return false;
            if (this.timeoutSeconds != null ? !this.timeoutSeconds.equals(that.timeoutSeconds) : that.timeoutSeconds != null) return false;
            if (this.tolerations != null ? !this.tolerations.equals(that.tolerations) : that.tolerations != null) return false;
            if (this.topologySpreadConstraints != null ? !this.topologySpreadConstraints.equals(that.topologySpreadConstraints) : that.topologySpreadConstraints != null) return false;
            return this.volumes != null ? this.volumes.equals(that.volumes) : that.volumes == null;
        }

        @Override
        public final int hashCode() {
            int result = this.containers.hashCode();
            result = 31 * result + (this.affinity != null ? this.affinity.hashCode() : 0);
            result = 31 * result + (this.automountServiceAccountToken != null ? this.automountServiceAccountToken.hashCode() : 0);
            result = 31 * result + (this.containerConcurrency != null ? this.containerConcurrency.hashCode() : 0);
            result = 31 * result + (this.dnsConfig != null ? this.dnsConfig.hashCode() : 0);
            result = 31 * result + (this.dnsPolicy != null ? this.dnsPolicy.hashCode() : 0);
            result = 31 * result + (this.enableServiceLinks != null ? this.enableServiceLinks.hashCode() : 0);
            result = 31 * result + (this.hostAliases != null ? this.hostAliases.hashCode() : 0);
            result = 31 * result + (this.hostIpc != null ? this.hostIpc.hashCode() : 0);
            result = 31 * result + (this.hostNetwork != null ? this.hostNetwork.hashCode() : 0);
            result = 31 * result + (this.hostPid != null ? this.hostPid.hashCode() : 0);
            result = 31 * result + (this.idleTimeoutSeconds != null ? this.idleTimeoutSeconds.hashCode() : 0);
            result = 31 * result + (this.imagePullSecrets != null ? this.imagePullSecrets.hashCode() : 0);
            result = 31 * result + (this.initContainers != null ? this.initContainers.hashCode() : 0);
            result = 31 * result + (this.nodeSelector != null ? this.nodeSelector.hashCode() : 0);
            result = 31 * result + (this.priorityClassName != null ? this.priorityClassName.hashCode() : 0);
            result = 31 * result + (this.responseStartTimeoutSeconds != null ? this.responseStartTimeoutSeconds.hashCode() : 0);
            result = 31 * result + (this.runtimeClassName != null ? this.runtimeClassName.hashCode() : 0);
            result = 31 * result + (this.schedulerName != null ? this.schedulerName.hashCode() : 0);
            result = 31 * result + (this.securityContext != null ? this.securityContext.hashCode() : 0);
            result = 31 * result + (this.serviceAccountName != null ? this.serviceAccountName.hashCode() : 0);
            result = 31 * result + (this.shareProcessNamespace != null ? this.shareProcessNamespace.hashCode() : 0);
            result = 31 * result + (this.timeoutSeconds != null ? this.timeoutSeconds.hashCode() : 0);
            result = 31 * result + (this.tolerations != null ? this.tolerations.hashCode() : 0);
            result = 31 * result + (this.topologySpreadConstraints != null ? this.topologySpreadConstraints.hashCode() : 0);
            result = 31 * result + (this.volumes != null ? this.volumes.hashCode() : 0);
            return result;
        }
    }
}
