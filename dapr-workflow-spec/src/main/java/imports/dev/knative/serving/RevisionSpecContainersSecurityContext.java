package imports.dev.knative.serving;

/**
 * SecurityContext defines the security options the container should be run with.
 * <p>
 * If set, the fields of SecurityContext override the equivalent fields of PodSecurityContext.
 * More info: https://kubernetes.io/docs/tasks/configure-pod-container/security-context/
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.161Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.RevisionSpecContainersSecurityContext")
@software.amazon.jsii.Jsii.Proxy(RevisionSpecContainersSecurityContext.Jsii$Proxy.class)
public interface RevisionSpecContainersSecurityContext extends software.amazon.jsii.JsiiSerializable {

    /**
     * AllowPrivilegeEscalation controls whether a process can gain more privileges than its parent process.
     * <p>
     * This bool directly controls if
     * the no_new_privs flag will be set on the container process.
     * AllowPrivilegeEscalation is true always when the container is:
     * <p>
     * <ol>
     * <li>run as Privileged</li>
     * <li>has CAP_SYS_ADMIN
     * Note that this field cannot be set when spec.os.name is windows.</li>
     * </ol>
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getAllowPrivilegeEscalation() {
        return null;
    }

    /**
     * The capabilities to add/drop when running containers.
     * <p>
     * Defaults to the default set of capabilities granted by the container runtime.
     * Note that this field cannot be set when spec.os.name is windows.
     * <p>
     * Default: the default set of capabilities granted by the container runtime.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.RevisionSpecContainersSecurityContextCapabilities getCapabilities() {
        return null;
    }

    /**
     * Run container in privileged mode.
     * <p>
     * This can only be set to explicitly to 'false'
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getPrivileged() {
        return null;
    }

    /**
     * Whether this container has a read-only root filesystem.
     * <p>
     * Default is false.
     * Note that this field cannot be set when spec.os.name is windows.
     * <p>
     * Default: false.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getReadOnlyRootFilesystem() {
        return null;
    }

    /**
     * The GID to run the entrypoint of the container process.
     * <p>
     * Uses runtime default if unset.
     * May also be set in PodSecurityContext.  If set in both SecurityContext and
     * PodSecurityContext, the value specified in SecurityContext takes precedence.
     * Note that this field cannot be set when spec.os.name is windows.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getRunAsGroup() {
        return null;
    }

    /**
     * Indicates that the container must run as a non-root user.
     * <p>
     * If true, the Kubelet will validate the image at runtime to ensure that it
     * does not run as UID 0 (root) and fail to start the container if it does.
     * If unset or false, no such validation will be performed.
     * May also be set in PodSecurityContext.  If set in both SecurityContext and
     * PodSecurityContext, the value specified in SecurityContext takes precedence.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getRunAsNonRoot() {
        return null;
    }

    /**
     * The UID to run the entrypoint of the container process.
     * <p>
     * Defaults to user specified in image metadata if unspecified.
     * May also be set in PodSecurityContext.  If set in both SecurityContext and
     * PodSecurityContext, the value specified in SecurityContext takes precedence.
     * Note that this field cannot be set when spec.os.name is windows.
     * <p>
     * Default: user specified in image metadata if unspecified.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getRunAsUser() {
        return null;
    }

    /**
     * The seccomp options to use by this container.
     * <p>
     * If seccomp options are
     * provided at both the pod &amp; container level, the container options
     * override the pod options.
     * Note that this field cannot be set when spec.os.name is windows.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.RevisionSpecContainersSecurityContextSeccompProfile getSeccompProfile() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link RevisionSpecContainersSecurityContext}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link RevisionSpecContainersSecurityContext}
     */
    public static final class Builder implements software.amazon.jsii.Builder<RevisionSpecContainersSecurityContext> {
        java.lang.Boolean allowPrivilegeEscalation;
        imports.dev.knative.serving.RevisionSpecContainersSecurityContextCapabilities capabilities;
        java.lang.Boolean privileged;
        java.lang.Boolean readOnlyRootFilesystem;
        java.lang.Number runAsGroup;
        java.lang.Boolean runAsNonRoot;
        java.lang.Number runAsUser;
        imports.dev.knative.serving.RevisionSpecContainersSecurityContextSeccompProfile seccompProfile;

        /**
         * Sets the value of {@link RevisionSpecContainersSecurityContext#getAllowPrivilegeEscalation}
         * @param allowPrivilegeEscalation AllowPrivilegeEscalation controls whether a process can gain more privileges than its parent process.
         *                                 This bool directly controls if
         *                                 the no_new_privs flag will be set on the container process.
         *                                 AllowPrivilegeEscalation is true always when the container is:
         *                                 <p>
         *                                 <ol>
         *                                 <li>run as Privileged</li>
         *                                 <li>has CAP_SYS_ADMIN
         *                                 Note that this field cannot be set when spec.os.name is windows.</li>
         *                                 </ol>
         * @return {@code this}
         */
        public Builder allowPrivilegeEscalation(java.lang.Boolean allowPrivilegeEscalation) {
            this.allowPrivilegeEscalation = allowPrivilegeEscalation;
            return this;
        }

        /**
         * Sets the value of {@link RevisionSpecContainersSecurityContext#getCapabilities}
         * @param capabilities The capabilities to add/drop when running containers.
         *                     Defaults to the default set of capabilities granted by the container runtime.
         *                     Note that this field cannot be set when spec.os.name is windows.
         * @return {@code this}
         */
        public Builder capabilities(imports.dev.knative.serving.RevisionSpecContainersSecurityContextCapabilities capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        /**
         * Sets the value of {@link RevisionSpecContainersSecurityContext#getPrivileged}
         * @param privileged Run container in privileged mode.
         *                   This can only be set to explicitly to 'false'
         * @return {@code this}
         */
        public Builder privileged(java.lang.Boolean privileged) {
            this.privileged = privileged;
            return this;
        }

        /**
         * Sets the value of {@link RevisionSpecContainersSecurityContext#getReadOnlyRootFilesystem}
         * @param readOnlyRootFilesystem Whether this container has a read-only root filesystem.
         *                               Default is false.
         *                               Note that this field cannot be set when spec.os.name is windows.
         * @return {@code this}
         */
        public Builder readOnlyRootFilesystem(java.lang.Boolean readOnlyRootFilesystem) {
            this.readOnlyRootFilesystem = readOnlyRootFilesystem;
            return this;
        }

        /**
         * Sets the value of {@link RevisionSpecContainersSecurityContext#getRunAsGroup}
         * @param runAsGroup The GID to run the entrypoint of the container process.
         *                   Uses runtime default if unset.
         *                   May also be set in PodSecurityContext.  If set in both SecurityContext and
         *                   PodSecurityContext, the value specified in SecurityContext takes precedence.
         *                   Note that this field cannot be set when spec.os.name is windows.
         * @return {@code this}
         */
        public Builder runAsGroup(java.lang.Number runAsGroup) {
            this.runAsGroup = runAsGroup;
            return this;
        }

        /**
         * Sets the value of {@link RevisionSpecContainersSecurityContext#getRunAsNonRoot}
         * @param runAsNonRoot Indicates that the container must run as a non-root user.
         *                     If true, the Kubelet will validate the image at runtime to ensure that it
         *                     does not run as UID 0 (root) and fail to start the container if it does.
         *                     If unset or false, no such validation will be performed.
         *                     May also be set in PodSecurityContext.  If set in both SecurityContext and
         *                     PodSecurityContext, the value specified in SecurityContext takes precedence.
         * @return {@code this}
         */
        public Builder runAsNonRoot(java.lang.Boolean runAsNonRoot) {
            this.runAsNonRoot = runAsNonRoot;
            return this;
        }

        /**
         * Sets the value of {@link RevisionSpecContainersSecurityContext#getRunAsUser}
         * @param runAsUser The UID to run the entrypoint of the container process.
         *                  Defaults to user specified in image metadata if unspecified.
         *                  May also be set in PodSecurityContext.  If set in both SecurityContext and
         *                  PodSecurityContext, the value specified in SecurityContext takes precedence.
         *                  Note that this field cannot be set when spec.os.name is windows.
         * @return {@code this}
         */
        public Builder runAsUser(java.lang.Number runAsUser) {
            this.runAsUser = runAsUser;
            return this;
        }

        /**
         * Sets the value of {@link RevisionSpecContainersSecurityContext#getSeccompProfile}
         * @param seccompProfile The seccomp options to use by this container.
         *                       If seccomp options are
         *                       provided at both the pod &amp; container level, the container options
         *                       override the pod options.
         *                       Note that this field cannot be set when spec.os.name is windows.
         * @return {@code this}
         */
        public Builder seccompProfile(imports.dev.knative.serving.RevisionSpecContainersSecurityContextSeccompProfile seccompProfile) {
            this.seccompProfile = seccompProfile;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link RevisionSpecContainersSecurityContext}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public RevisionSpecContainersSecurityContext build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link RevisionSpecContainersSecurityContext}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements RevisionSpecContainersSecurityContext {
        private final java.lang.Boolean allowPrivilegeEscalation;
        private final imports.dev.knative.serving.RevisionSpecContainersSecurityContextCapabilities capabilities;
        private final java.lang.Boolean privileged;
        private final java.lang.Boolean readOnlyRootFilesystem;
        private final java.lang.Number runAsGroup;
        private final java.lang.Boolean runAsNonRoot;
        private final java.lang.Number runAsUser;
        private final imports.dev.knative.serving.RevisionSpecContainersSecurityContextSeccompProfile seccompProfile;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.allowPrivilegeEscalation = software.amazon.jsii.Kernel.get(this, "allowPrivilegeEscalation", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.capabilities = software.amazon.jsii.Kernel.get(this, "capabilities", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.RevisionSpecContainersSecurityContextCapabilities.class));
            this.privileged = software.amazon.jsii.Kernel.get(this, "privileged", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.readOnlyRootFilesystem = software.amazon.jsii.Kernel.get(this, "readOnlyRootFilesystem", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.runAsGroup = software.amazon.jsii.Kernel.get(this, "runAsGroup", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.runAsNonRoot = software.amazon.jsii.Kernel.get(this, "runAsNonRoot", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.runAsUser = software.amazon.jsii.Kernel.get(this, "runAsUser", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.seccompProfile = software.amazon.jsii.Kernel.get(this, "seccompProfile", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.RevisionSpecContainersSecurityContextSeccompProfile.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.allowPrivilegeEscalation = builder.allowPrivilegeEscalation;
            this.capabilities = builder.capabilities;
            this.privileged = builder.privileged;
            this.readOnlyRootFilesystem = builder.readOnlyRootFilesystem;
            this.runAsGroup = builder.runAsGroup;
            this.runAsNonRoot = builder.runAsNonRoot;
            this.runAsUser = builder.runAsUser;
            this.seccompProfile = builder.seccompProfile;
        }

        @Override
        public final java.lang.Boolean getAllowPrivilegeEscalation() {
            return this.allowPrivilegeEscalation;
        }

        @Override
        public final imports.dev.knative.serving.RevisionSpecContainersSecurityContextCapabilities getCapabilities() {
            return this.capabilities;
        }

        @Override
        public final java.lang.Boolean getPrivileged() {
            return this.privileged;
        }

        @Override
        public final java.lang.Boolean getReadOnlyRootFilesystem() {
            return this.readOnlyRootFilesystem;
        }

        @Override
        public final java.lang.Number getRunAsGroup() {
            return this.runAsGroup;
        }

        @Override
        public final java.lang.Boolean getRunAsNonRoot() {
            return this.runAsNonRoot;
        }

        @Override
        public final java.lang.Number getRunAsUser() {
            return this.runAsUser;
        }

        @Override
        public final imports.dev.knative.serving.RevisionSpecContainersSecurityContextSeccompProfile getSeccompProfile() {
            return this.seccompProfile;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getAllowPrivilegeEscalation() != null) {
                data.set("allowPrivilegeEscalation", om.valueToTree(this.getAllowPrivilegeEscalation()));
            }
            if (this.getCapabilities() != null) {
                data.set("capabilities", om.valueToTree(this.getCapabilities()));
            }
            if (this.getPrivileged() != null) {
                data.set("privileged", om.valueToTree(this.getPrivileged()));
            }
            if (this.getReadOnlyRootFilesystem() != null) {
                data.set("readOnlyRootFilesystem", om.valueToTree(this.getReadOnlyRootFilesystem()));
            }
            if (this.getRunAsGroup() != null) {
                data.set("runAsGroup", om.valueToTree(this.getRunAsGroup()));
            }
            if (this.getRunAsNonRoot() != null) {
                data.set("runAsNonRoot", om.valueToTree(this.getRunAsNonRoot()));
            }
            if (this.getRunAsUser() != null) {
                data.set("runAsUser", om.valueToTree(this.getRunAsUser()));
            }
            if (this.getSeccompProfile() != null) {
                data.set("seccompProfile", om.valueToTree(this.getSeccompProfile()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.RevisionSpecContainersSecurityContext"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RevisionSpecContainersSecurityContext.Jsii$Proxy that = (RevisionSpecContainersSecurityContext.Jsii$Proxy) o;

            if (this.allowPrivilegeEscalation != null ? !this.allowPrivilegeEscalation.equals(that.allowPrivilegeEscalation) : that.allowPrivilegeEscalation != null) return false;
            if (this.capabilities != null ? !this.capabilities.equals(that.capabilities) : that.capabilities != null) return false;
            if (this.privileged != null ? !this.privileged.equals(that.privileged) : that.privileged != null) return false;
            if (this.readOnlyRootFilesystem != null ? !this.readOnlyRootFilesystem.equals(that.readOnlyRootFilesystem) : that.readOnlyRootFilesystem != null) return false;
            if (this.runAsGroup != null ? !this.runAsGroup.equals(that.runAsGroup) : that.runAsGroup != null) return false;
            if (this.runAsNonRoot != null ? !this.runAsNonRoot.equals(that.runAsNonRoot) : that.runAsNonRoot != null) return false;
            if (this.runAsUser != null ? !this.runAsUser.equals(that.runAsUser) : that.runAsUser != null) return false;
            return this.seccompProfile != null ? this.seccompProfile.equals(that.seccompProfile) : that.seccompProfile == null;
        }

        @Override
        public final int hashCode() {
            int result = this.allowPrivilegeEscalation != null ? this.allowPrivilegeEscalation.hashCode() : 0;
            result = 31 * result + (this.capabilities != null ? this.capabilities.hashCode() : 0);
            result = 31 * result + (this.privileged != null ? this.privileged.hashCode() : 0);
            result = 31 * result + (this.readOnlyRootFilesystem != null ? this.readOnlyRootFilesystem.hashCode() : 0);
            result = 31 * result + (this.runAsGroup != null ? this.runAsGroup.hashCode() : 0);
            result = 31 * result + (this.runAsNonRoot != null ? this.runAsNonRoot.hashCode() : 0);
            result = 31 * result + (this.runAsUser != null ? this.runAsUser.hashCode() : 0);
            result = 31 * result + (this.seccompProfile != null ? this.seccompProfile.hashCode() : 0);
            return result;
        }
    }
}
