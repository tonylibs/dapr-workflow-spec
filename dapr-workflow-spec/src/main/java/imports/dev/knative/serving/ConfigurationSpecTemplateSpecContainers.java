package imports.dev.knative.serving;

/**
 * A single application container that you want to run within a pod.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.101Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ConfigurationSpecTemplateSpecContainers")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecTemplateSpecContainers.Jsii$Proxy.class)
public interface ConfigurationSpecTemplateSpecContainers extends software.amazon.jsii.JsiiSerializable {

    /**
     * Arguments to the entrypoint.
     * <p>
     * The container image's CMD is used if this is not provided.
     * Variable references $(VAR_NAME) are expanded using the container's environment. If a variable
     * cannot be resolved, the reference in the input string will be unchanged. Double $$ are reduced
     * to a single $, which allows for escaping the $(VAR_NAME) syntax: i.e. "$$(VAR_NAME)" will
     * produce the string literal "$(VAR_NAME)". Escaped references will never be expanded, regardless
     * of whether the variable exists or not. Cannot be updated.
     * More info: https://kubernetes.io/docs/tasks/inject-data-application/define-command-argument-container/#running-a-command-in-a-shell
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getArgs() {
        return null;
    }

    /**
     * Entrypoint array.
     * <p>
     * Not executed within a shell.
     * The container image's ENTRYPOINT is used if this is not provided.
     * Variable references $(VAR_NAME) are expanded using the container's environment. If a variable
     * cannot be resolved, the reference in the input string will be unchanged. Double $$ are reduced
     * to a single $, which allows for escaping the $(VAR_NAME) syntax: i.e. "$$(VAR_NAME)" will
     * produce the string literal "$(VAR_NAME)". Escaped references will never be expanded, regardless
     * of whether the variable exists or not. Cannot be updated.
     * More info: https://kubernetes.io/docs/tasks/inject-data-application/define-command-argument-container/#running-a-command-in-a-shell
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getCommand() {
        return null;
    }

    /**
     * List of environment variables to set in the container.
     * <p>
     * Cannot be updated.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnv> getEnv() {
        return null;
    }

    /**
     * List of sources to populate environment variables in the container.
     * <p>
     * The keys defined within a source may consist of any printable ASCII characters except '='.
     * When a key exists in multiple
     * sources, the value associated with the last source will take precedence.
     * Values defined by an Env with a duplicate key will take precedence.
     * Cannot be updated.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvFrom> getEnvFrom() {
        return null;
    }

    /**
     * Container image name.
     * <p>
     * More info: https://kubernetes.io/docs/concepts/containers/images
     * This field is optional to allow higher level config management to default or override
     * container images in workload controllers like Deployments and StatefulSets.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getImage() {
        return null;
    }

    /**
     * Image pull policy.
     * <p>
     * One of Always, Never, IfNotPresent.
     * Defaults to Always if :latest tag is specified, or IfNotPresent otherwise.
     * Cannot be updated.
     * More info: https://kubernetes.io/docs/concepts/containers/images#updating-images
     * <p>
     * Default: Always if :latest tag is specified, or IfNotPresent otherwise.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getImagePullPolicy() {
        return null;
    }

    /**
     * Periodic probe of container liveness.
     * <p>
     * Container will be restarted if the probe fails.
     * Cannot be updated.
     * More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle#container-probes
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersLivenessProbe getLivenessProbe() {
        return null;
    }

    /**
     * Name of the container specified as a DNS_LABEL.
     * <p>
     * Each container in a pod must have a unique name (DNS_LABEL).
     * Cannot be updated.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getName() {
        return null;
    }

    /**
     * List of ports to expose from the container.
     * <p>
     * Not specifying a port here
     * DOES NOT prevent that port from being exposed. Any port which is
     * listening on the default "0.0.0.0" address inside a container will be
     * accessible from the network.
     * Modifying this array with strategic merge patch may corrupt the data.
     * For more information See https://github.com/kubernetes/kubernetes/issues/108255.
     * Cannot be updated.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersPorts> getPorts() {
        return null;
    }

    /**
     * Periodic probe of container service readiness.
     * <p>
     * Container will be removed from service endpoints if the probe fails.
     * Cannot be updated.
     * More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle#container-probes
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersReadinessProbe getReadinessProbe() {
        return null;
    }

    /**
     * Compute Resources required by this container.
     * <p>
     * Cannot be updated.
     * More info: https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersResources getResources() {
        return null;
    }

    /**
     * SecurityContext defines the security options the container should be run with.
     * <p>
     * If set, the fields of SecurityContext override the equivalent fields of PodSecurityContext.
     * More info: https://kubernetes.io/docs/tasks/configure-pod-container/security-context/
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersSecurityContext getSecurityContext() {
        return null;
    }

    /**
     * StartupProbe indicates that the Pod has successfully initialized.
     * <p>
     * If specified, no other probes are executed until this completes successfully.
     * If this probe fails, the Pod will be restarted, just as if the livenessProbe failed.
     * This can be used to provide different probe parameters at the beginning of a Pod's lifecycle,
     * when it might take a long time to load data or warm a cache, than during steady-state operation.
     * This cannot be updated.
     * More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle#container-probes
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbe getStartupProbe() {
        return null;
    }

    /**
     * Optional: Path at which the file to which the container's termination message will be written is mounted into the container's filesystem.
     * <p>
     * Message written is intended to be brief final status, such as an assertion failure message.
     * Will be truncated by the node if greater than 4096 bytes. The total message length across
     * all containers will be limited to 12kb.
     * Defaults to /dev/termination-log.
     * Cannot be updated.
     * <p>
     * Default: dev/termination-log.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getTerminationMessagePath() {
        return null;
    }

    /**
     * Indicate how the termination message should be populated.
     * <p>
     * File will use the contents of
     * terminationMessagePath to populate the container status message on both success and failure.
     * FallbackToLogsOnError will use the last chunk of container log output if the termination
     * message file is empty and the container exited with an error.
     * The log output is limited to 2048 bytes or 80 lines, whichever is smaller.
     * Defaults to File.
     * Cannot be updated.
     * <p>
     * Default: File.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getTerminationMessagePolicy() {
        return null;
    }

    /**
     * Pod volumes to mount into the container's filesystem.
     * <p>
     * Cannot be updated.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersVolumeMounts> getVolumeMounts() {
        return null;
    }

    /**
     * Container's working directory.
     * <p>
     * If not specified, the container runtime's default will be used, which
     * might be configured in the container image.
     * Cannot be updated.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getWorkingDir() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecTemplateSpecContainers}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecTemplateSpecContainers}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecTemplateSpecContainers> {
        java.util.List<java.lang.String> args;
        java.util.List<java.lang.String> command;
        java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnv> env;
        java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvFrom> envFrom;
        java.lang.String image;
        java.lang.String imagePullPolicy;
        imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersLivenessProbe livenessProbe;
        java.lang.String name;
        java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersPorts> ports;
        imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersReadinessProbe readinessProbe;
        imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersResources resources;
        imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersSecurityContext securityContext;
        imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbe startupProbe;
        java.lang.String terminationMessagePath;
        java.lang.String terminationMessagePolicy;
        java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersVolumeMounts> volumeMounts;
        java.lang.String workingDir;

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getArgs}
         * @param args Arguments to the entrypoint.
         *             The container image's CMD is used if this is not provided.
         *             Variable references $(VAR_NAME) are expanded using the container's environment. If a variable
         *             cannot be resolved, the reference in the input string will be unchanged. Double $$ are reduced
         *             to a single $, which allows for escaping the $(VAR_NAME) syntax: i.e. "$$(VAR_NAME)" will
         *             produce the string literal "$(VAR_NAME)". Escaped references will never be expanded, regardless
         *             of whether the variable exists or not. Cannot be updated.
         *             More info: https://kubernetes.io/docs/tasks/inject-data-application/define-command-argument-container/#running-a-command-in-a-shell
         * @return {@code this}
         */
        public Builder args(java.util.List<java.lang.String> args) {
            this.args = args;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getCommand}
         * @param command Entrypoint array.
         *                Not executed within a shell.
         *                The container image's ENTRYPOINT is used if this is not provided.
         *                Variable references $(VAR_NAME) are expanded using the container's environment. If a variable
         *                cannot be resolved, the reference in the input string will be unchanged. Double $$ are reduced
         *                to a single $, which allows for escaping the $(VAR_NAME) syntax: i.e. "$$(VAR_NAME)" will
         *                produce the string literal "$(VAR_NAME)". Escaped references will never be expanded, regardless
         *                of whether the variable exists or not. Cannot be updated.
         *                More info: https://kubernetes.io/docs/tasks/inject-data-application/define-command-argument-container/#running-a-command-in-a-shell
         * @return {@code this}
         */
        public Builder command(java.util.List<java.lang.String> command) {
            this.command = command;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getEnv}
         * @param env List of environment variables to set in the container.
         *            Cannot be updated.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder env(java.util.List<? extends imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnv> env) {
            this.env = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnv>)env;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getEnvFrom}
         * @param envFrom List of sources to populate environment variables in the container.
         *                The keys defined within a source may consist of any printable ASCII characters except '='.
         *                When a key exists in multiple
         *                sources, the value associated with the last source will take precedence.
         *                Values defined by an Env with a duplicate key will take precedence.
         *                Cannot be updated.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder envFrom(java.util.List<? extends imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvFrom> envFrom) {
            this.envFrom = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvFrom>)envFrom;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getImage}
         * @param image Container image name.
         *              More info: https://kubernetes.io/docs/concepts/containers/images
         *              This field is optional to allow higher level config management to default or override
         *              container images in workload controllers like Deployments and StatefulSets.
         * @return {@code this}
         */
        public Builder image(java.lang.String image) {
            this.image = image;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getImagePullPolicy}
         * @param imagePullPolicy Image pull policy.
         *                        One of Always, Never, IfNotPresent.
         *                        Defaults to Always if :latest tag is specified, or IfNotPresent otherwise.
         *                        Cannot be updated.
         *                        More info: https://kubernetes.io/docs/concepts/containers/images#updating-images
         * @return {@code this}
         */
        public Builder imagePullPolicy(java.lang.String imagePullPolicy) {
            this.imagePullPolicy = imagePullPolicy;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getLivenessProbe}
         * @param livenessProbe Periodic probe of container liveness.
         *                      Container will be restarted if the probe fails.
         *                      Cannot be updated.
         *                      More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle#container-probes
         * @return {@code this}
         */
        public Builder livenessProbe(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersLivenessProbe livenessProbe) {
            this.livenessProbe = livenessProbe;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getName}
         * @param name Name of the container specified as a DNS_LABEL.
         *             Each container in a pod must have a unique name (DNS_LABEL).
         *             Cannot be updated.
         * @return {@code this}
         */
        public Builder name(java.lang.String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getPorts}
         * @param ports List of ports to expose from the container.
         *              Not specifying a port here
         *              DOES NOT prevent that port from being exposed. Any port which is
         *              listening on the default "0.0.0.0" address inside a container will be
         *              accessible from the network.
         *              Modifying this array with strategic merge patch may corrupt the data.
         *              For more information See https://github.com/kubernetes/kubernetes/issues/108255.
         *              Cannot be updated.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder ports(java.util.List<? extends imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersPorts> ports) {
            this.ports = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersPorts>)ports;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getReadinessProbe}
         * @param readinessProbe Periodic probe of container service readiness.
         *                       Container will be removed from service endpoints if the probe fails.
         *                       Cannot be updated.
         *                       More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle#container-probes
         * @return {@code this}
         */
        public Builder readinessProbe(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersReadinessProbe readinessProbe) {
            this.readinessProbe = readinessProbe;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getResources}
         * @param resources Compute Resources required by this container.
         *                  Cannot be updated.
         *                  More info: https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
         * @return {@code this}
         */
        public Builder resources(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersResources resources) {
            this.resources = resources;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getSecurityContext}
         * @param securityContext SecurityContext defines the security options the container should be run with.
         *                        If set, the fields of SecurityContext override the equivalent fields of PodSecurityContext.
         *                        More info: https://kubernetes.io/docs/tasks/configure-pod-container/security-context/
         * @return {@code this}
         */
        public Builder securityContext(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersSecurityContext securityContext) {
            this.securityContext = securityContext;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getStartupProbe}
         * @param startupProbe StartupProbe indicates that the Pod has successfully initialized.
         *                     If specified, no other probes are executed until this completes successfully.
         *                     If this probe fails, the Pod will be restarted, just as if the livenessProbe failed.
         *                     This can be used to provide different probe parameters at the beginning of a Pod's lifecycle,
         *                     when it might take a long time to load data or warm a cache, than during steady-state operation.
         *                     This cannot be updated.
         *                     More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle#container-probes
         * @return {@code this}
         */
        public Builder startupProbe(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbe startupProbe) {
            this.startupProbe = startupProbe;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getTerminationMessagePath}
         * @param terminationMessagePath Optional: Path at which the file to which the container's termination message will be written is mounted into the container's filesystem.
         *                               Message written is intended to be brief final status, such as an assertion failure message.
         *                               Will be truncated by the node if greater than 4096 bytes. The total message length across
         *                               all containers will be limited to 12kb.
         *                               Defaults to /dev/termination-log.
         *                               Cannot be updated.
         * @return {@code this}
         */
        public Builder terminationMessagePath(java.lang.String terminationMessagePath) {
            this.terminationMessagePath = terminationMessagePath;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getTerminationMessagePolicy}
         * @param terminationMessagePolicy Indicate how the termination message should be populated.
         *                                 File will use the contents of
         *                                 terminationMessagePath to populate the container status message on both success and failure.
         *                                 FallbackToLogsOnError will use the last chunk of container log output if the termination
         *                                 message file is empty and the container exited with an error.
         *                                 The log output is limited to 2048 bytes or 80 lines, whichever is smaller.
         *                                 Defaults to File.
         *                                 Cannot be updated.
         * @return {@code this}
         */
        public Builder terminationMessagePolicy(java.lang.String terminationMessagePolicy) {
            this.terminationMessagePolicy = terminationMessagePolicy;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getVolumeMounts}
         * @param volumeMounts Pod volumes to mount into the container's filesystem.
         *                     Cannot be updated.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder volumeMounts(java.util.List<? extends imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersVolumeMounts> volumeMounts) {
            this.volumeMounts = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersVolumeMounts>)volumeMounts;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainers#getWorkingDir}
         * @param workingDir Container's working directory.
         *                   If not specified, the container runtime's default will be used, which
         *                   might be configured in the container image.
         *                   Cannot be updated.
         * @return {@code this}
         */
        public Builder workingDir(java.lang.String workingDir) {
            this.workingDir = workingDir;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecTemplateSpecContainers}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecTemplateSpecContainers build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecTemplateSpecContainers}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecTemplateSpecContainers {
        private final java.util.List<java.lang.String> args;
        private final java.util.List<java.lang.String> command;
        private final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnv> env;
        private final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvFrom> envFrom;
        private final java.lang.String image;
        private final java.lang.String imagePullPolicy;
        private final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersLivenessProbe livenessProbe;
        private final java.lang.String name;
        private final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersPorts> ports;
        private final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersReadinessProbe readinessProbe;
        private final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersResources resources;
        private final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersSecurityContext securityContext;
        private final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbe startupProbe;
        private final java.lang.String terminationMessagePath;
        private final java.lang.String terminationMessagePolicy;
        private final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersVolumeMounts> volumeMounts;
        private final java.lang.String workingDir;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.args = software.amazon.jsii.Kernel.get(this, "args", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.command = software.amazon.jsii.Kernel.get(this, "command", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.env = software.amazon.jsii.Kernel.get(this, "env", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnv.class)));
            this.envFrom = software.amazon.jsii.Kernel.get(this, "envFrom", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvFrom.class)));
            this.image = software.amazon.jsii.Kernel.get(this, "image", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.imagePullPolicy = software.amazon.jsii.Kernel.get(this, "imagePullPolicy", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.livenessProbe = software.amazon.jsii.Kernel.get(this, "livenessProbe", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersLivenessProbe.class));
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.ports = software.amazon.jsii.Kernel.get(this, "ports", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersPorts.class)));
            this.readinessProbe = software.amazon.jsii.Kernel.get(this, "readinessProbe", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersReadinessProbe.class));
            this.resources = software.amazon.jsii.Kernel.get(this, "resources", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersResources.class));
            this.securityContext = software.amazon.jsii.Kernel.get(this, "securityContext", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersSecurityContext.class));
            this.startupProbe = software.amazon.jsii.Kernel.get(this, "startupProbe", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbe.class));
            this.terminationMessagePath = software.amazon.jsii.Kernel.get(this, "terminationMessagePath", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.terminationMessagePolicy = software.amazon.jsii.Kernel.get(this, "terminationMessagePolicy", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.volumeMounts = software.amazon.jsii.Kernel.get(this, "volumeMounts", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersVolumeMounts.class)));
            this.workingDir = software.amazon.jsii.Kernel.get(this, "workingDir", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.args = builder.args;
            this.command = builder.command;
            this.env = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnv>)builder.env;
            this.envFrom = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvFrom>)builder.envFrom;
            this.image = builder.image;
            this.imagePullPolicy = builder.imagePullPolicy;
            this.livenessProbe = builder.livenessProbe;
            this.name = builder.name;
            this.ports = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersPorts>)builder.ports;
            this.readinessProbe = builder.readinessProbe;
            this.resources = builder.resources;
            this.securityContext = builder.securityContext;
            this.startupProbe = builder.startupProbe;
            this.terminationMessagePath = builder.terminationMessagePath;
            this.terminationMessagePolicy = builder.terminationMessagePolicy;
            this.volumeMounts = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersVolumeMounts>)builder.volumeMounts;
            this.workingDir = builder.workingDir;
        }

        @Override
        public final java.util.List<java.lang.String> getArgs() {
            return this.args;
        }

        @Override
        public final java.util.List<java.lang.String> getCommand() {
            return this.command;
        }

        @Override
        public final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnv> getEnv() {
            return this.env;
        }

        @Override
        public final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersEnvFrom> getEnvFrom() {
            return this.envFrom;
        }

        @Override
        public final java.lang.String getImage() {
            return this.image;
        }

        @Override
        public final java.lang.String getImagePullPolicy() {
            return this.imagePullPolicy;
        }

        @Override
        public final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersLivenessProbe getLivenessProbe() {
            return this.livenessProbe;
        }

        @Override
        public final java.lang.String getName() {
            return this.name;
        }

        @Override
        public final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersPorts> getPorts() {
            return this.ports;
        }

        @Override
        public final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersReadinessProbe getReadinessProbe() {
            return this.readinessProbe;
        }

        @Override
        public final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersResources getResources() {
            return this.resources;
        }

        @Override
        public final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersSecurityContext getSecurityContext() {
            return this.securityContext;
        }

        @Override
        public final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbe getStartupProbe() {
            return this.startupProbe;
        }

        @Override
        public final java.lang.String getTerminationMessagePath() {
            return this.terminationMessagePath;
        }

        @Override
        public final java.lang.String getTerminationMessagePolicy() {
            return this.terminationMessagePolicy;
        }

        @Override
        public final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersVolumeMounts> getVolumeMounts() {
            return this.volumeMounts;
        }

        @Override
        public final java.lang.String getWorkingDir() {
            return this.workingDir;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getArgs() != null) {
                data.set("args", om.valueToTree(this.getArgs()));
            }
            if (this.getCommand() != null) {
                data.set("command", om.valueToTree(this.getCommand()));
            }
            if (this.getEnv() != null) {
                data.set("env", om.valueToTree(this.getEnv()));
            }
            if (this.getEnvFrom() != null) {
                data.set("envFrom", om.valueToTree(this.getEnvFrom()));
            }
            if (this.getImage() != null) {
                data.set("image", om.valueToTree(this.getImage()));
            }
            if (this.getImagePullPolicy() != null) {
                data.set("imagePullPolicy", om.valueToTree(this.getImagePullPolicy()));
            }
            if (this.getLivenessProbe() != null) {
                data.set("livenessProbe", om.valueToTree(this.getLivenessProbe()));
            }
            if (this.getName() != null) {
                data.set("name", om.valueToTree(this.getName()));
            }
            if (this.getPorts() != null) {
                data.set("ports", om.valueToTree(this.getPorts()));
            }
            if (this.getReadinessProbe() != null) {
                data.set("readinessProbe", om.valueToTree(this.getReadinessProbe()));
            }
            if (this.getResources() != null) {
                data.set("resources", om.valueToTree(this.getResources()));
            }
            if (this.getSecurityContext() != null) {
                data.set("securityContext", om.valueToTree(this.getSecurityContext()));
            }
            if (this.getStartupProbe() != null) {
                data.set("startupProbe", om.valueToTree(this.getStartupProbe()));
            }
            if (this.getTerminationMessagePath() != null) {
                data.set("terminationMessagePath", om.valueToTree(this.getTerminationMessagePath()));
            }
            if (this.getTerminationMessagePolicy() != null) {
                data.set("terminationMessagePolicy", om.valueToTree(this.getTerminationMessagePolicy()));
            }
            if (this.getVolumeMounts() != null) {
                data.set("volumeMounts", om.valueToTree(this.getVolumeMounts()));
            }
            if (this.getWorkingDir() != null) {
                data.set("workingDir", om.valueToTree(this.getWorkingDir()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ConfigurationSpecTemplateSpecContainers"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecTemplateSpecContainers.Jsii$Proxy that = (ConfigurationSpecTemplateSpecContainers.Jsii$Proxy) o;

            if (this.args != null ? !this.args.equals(that.args) : that.args != null) return false;
            if (this.command != null ? !this.command.equals(that.command) : that.command != null) return false;
            if (this.env != null ? !this.env.equals(that.env) : that.env != null) return false;
            if (this.envFrom != null ? !this.envFrom.equals(that.envFrom) : that.envFrom != null) return false;
            if (this.image != null ? !this.image.equals(that.image) : that.image != null) return false;
            if (this.imagePullPolicy != null ? !this.imagePullPolicy.equals(that.imagePullPolicy) : that.imagePullPolicy != null) return false;
            if (this.livenessProbe != null ? !this.livenessProbe.equals(that.livenessProbe) : that.livenessProbe != null) return false;
            if (this.name != null ? !this.name.equals(that.name) : that.name != null) return false;
            if (this.ports != null ? !this.ports.equals(that.ports) : that.ports != null) return false;
            if (this.readinessProbe != null ? !this.readinessProbe.equals(that.readinessProbe) : that.readinessProbe != null) return false;
            if (this.resources != null ? !this.resources.equals(that.resources) : that.resources != null) return false;
            if (this.securityContext != null ? !this.securityContext.equals(that.securityContext) : that.securityContext != null) return false;
            if (this.startupProbe != null ? !this.startupProbe.equals(that.startupProbe) : that.startupProbe != null) return false;
            if (this.terminationMessagePath != null ? !this.terminationMessagePath.equals(that.terminationMessagePath) : that.terminationMessagePath != null) return false;
            if (this.terminationMessagePolicy != null ? !this.terminationMessagePolicy.equals(that.terminationMessagePolicy) : that.terminationMessagePolicy != null) return false;
            if (this.volumeMounts != null ? !this.volumeMounts.equals(that.volumeMounts) : that.volumeMounts != null) return false;
            return this.workingDir != null ? this.workingDir.equals(that.workingDir) : that.workingDir == null;
        }

        @Override
        public final int hashCode() {
            int result = this.args != null ? this.args.hashCode() : 0;
            result = 31 * result + (this.command != null ? this.command.hashCode() : 0);
            result = 31 * result + (this.env != null ? this.env.hashCode() : 0);
            result = 31 * result + (this.envFrom != null ? this.envFrom.hashCode() : 0);
            result = 31 * result + (this.image != null ? this.image.hashCode() : 0);
            result = 31 * result + (this.imagePullPolicy != null ? this.imagePullPolicy.hashCode() : 0);
            result = 31 * result + (this.livenessProbe != null ? this.livenessProbe.hashCode() : 0);
            result = 31 * result + (this.name != null ? this.name.hashCode() : 0);
            result = 31 * result + (this.ports != null ? this.ports.hashCode() : 0);
            result = 31 * result + (this.readinessProbe != null ? this.readinessProbe.hashCode() : 0);
            result = 31 * result + (this.resources != null ? this.resources.hashCode() : 0);
            result = 31 * result + (this.securityContext != null ? this.securityContext.hashCode() : 0);
            result = 31 * result + (this.startupProbe != null ? this.startupProbe.hashCode() : 0);
            result = 31 * result + (this.terminationMessagePath != null ? this.terminationMessagePath.hashCode() : 0);
            result = 31 * result + (this.terminationMessagePolicy != null ? this.terminationMessagePolicy.hashCode() : 0);
            result = 31 * result + (this.volumeMounts != null ? this.volumeMounts.hashCode() : 0);
            result = 31 * result + (this.workingDir != null ? this.workingDir.hashCode() : 0);
            return result;
        }
    }
}
