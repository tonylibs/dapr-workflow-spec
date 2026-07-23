package imports.dev.knative.serving;

/**
 * Periodic probe of container service readiness.
 * <p>
 * Container will be removed from service endpoints if the probe fails.
 * Cannot be updated.
 * More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle#container-probes
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.188Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ServiceSpecTemplateSpecContainersReadinessProbe")
@software.amazon.jsii.Jsii.Proxy(ServiceSpecTemplateSpecContainersReadinessProbe.Jsii$Proxy.class)
public interface ServiceSpecTemplateSpecContainersReadinessProbe extends software.amazon.jsii.JsiiSerializable {

    /**
     * Exec specifies a command to execute in the container.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeExec getExec() {
        return null;
    }

    /**
     * Minimum consecutive failures for the probe to be considered failed after having succeeded.
     * <p>
     * Defaults to 3. Minimum value is 1.
     * <p>
     * Default: 3. Minimum value is 1.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getFailureThreshold() {
        return null;
    }

    /**
     * GRPC specifies a GRPC HealthCheckRequest.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeGrpc getGrpc() {
        return null;
    }

    /**
     * HTTPGet specifies an HTTP GET request to perform.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeHttpGet getHttpGet() {
        return null;
    }

    /**
     * Number of seconds after the container has started before liveness probes are initiated.
     * <p>
     * More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle#container-probes
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getInitialDelaySeconds() {
        return null;
    }

    /**
     * How often (in seconds) to perform the probe.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getPeriodSeconds() {
        return null;
    }

    /**
     * Minimum consecutive successes for the probe to be considered successful after having failed.
     * <p>
     * Defaults to 1. Must be 1 for liveness and startup. Minimum value is 1.
     * <p>
     * Default: 1. Must be 1 for liveness and startup. Minimum value is 1.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getSuccessThreshold() {
        return null;
    }

    /**
     * TCPSocket specifies a connection to a TCP port.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeTcpSocket getTcpSocket() {
        return null;
    }

    /**
     * Number of seconds after which the probe times out.
     * <p>
     * Defaults to 1 second. Minimum value is 1.
     * More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle#container-probes
     * <p>
     * Default: 1 second. Minimum value is 1.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getTimeoutSeconds() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ServiceSpecTemplateSpecContainersReadinessProbe}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ServiceSpecTemplateSpecContainersReadinessProbe}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ServiceSpecTemplateSpecContainersReadinessProbe> {
        imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeExec exec;
        java.lang.Number failureThreshold;
        imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeGrpc grpc;
        imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeHttpGet httpGet;
        java.lang.Number initialDelaySeconds;
        java.lang.Number periodSeconds;
        java.lang.Number successThreshold;
        imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeTcpSocket tcpSocket;
        java.lang.Number timeoutSeconds;

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecContainersReadinessProbe#getExec}
         * @param exec Exec specifies a command to execute in the container.
         * @return {@code this}
         */
        public Builder exec(imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeExec exec) {
            this.exec = exec;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecContainersReadinessProbe#getFailureThreshold}
         * @param failureThreshold Minimum consecutive failures for the probe to be considered failed after having succeeded.
         *                         Defaults to 3. Minimum value is 1.
         * @return {@code this}
         */
        public Builder failureThreshold(java.lang.Number failureThreshold) {
            this.failureThreshold = failureThreshold;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecContainersReadinessProbe#getGrpc}
         * @param grpc GRPC specifies a GRPC HealthCheckRequest.
         * @return {@code this}
         */
        public Builder grpc(imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeGrpc grpc) {
            this.grpc = grpc;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecContainersReadinessProbe#getHttpGet}
         * @param httpGet HTTPGet specifies an HTTP GET request to perform.
         * @return {@code this}
         */
        public Builder httpGet(imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeHttpGet httpGet) {
            this.httpGet = httpGet;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecContainersReadinessProbe#getInitialDelaySeconds}
         * @param initialDelaySeconds Number of seconds after the container has started before liveness probes are initiated.
         *                            More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle#container-probes
         * @return {@code this}
         */
        public Builder initialDelaySeconds(java.lang.Number initialDelaySeconds) {
            this.initialDelaySeconds = initialDelaySeconds;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecContainersReadinessProbe#getPeriodSeconds}
         * @param periodSeconds How often (in seconds) to perform the probe.
         * @return {@code this}
         */
        public Builder periodSeconds(java.lang.Number periodSeconds) {
            this.periodSeconds = periodSeconds;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecContainersReadinessProbe#getSuccessThreshold}
         * @param successThreshold Minimum consecutive successes for the probe to be considered successful after having failed.
         *                         Defaults to 1. Must be 1 for liveness and startup. Minimum value is 1.
         * @return {@code this}
         */
        public Builder successThreshold(java.lang.Number successThreshold) {
            this.successThreshold = successThreshold;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecContainersReadinessProbe#getTcpSocket}
         * @param tcpSocket TCPSocket specifies a connection to a TCP port.
         * @return {@code this}
         */
        public Builder tcpSocket(imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeTcpSocket tcpSocket) {
            this.tcpSocket = tcpSocket;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecContainersReadinessProbe#getTimeoutSeconds}
         * @param timeoutSeconds Number of seconds after which the probe times out.
         *                       Defaults to 1 second. Minimum value is 1.
         *                       More info: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle#container-probes
         * @return {@code this}
         */
        public Builder timeoutSeconds(java.lang.Number timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ServiceSpecTemplateSpecContainersReadinessProbe}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ServiceSpecTemplateSpecContainersReadinessProbe build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ServiceSpecTemplateSpecContainersReadinessProbe}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ServiceSpecTemplateSpecContainersReadinessProbe {
        private final imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeExec exec;
        private final java.lang.Number failureThreshold;
        private final imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeGrpc grpc;
        private final imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeHttpGet httpGet;
        private final java.lang.Number initialDelaySeconds;
        private final java.lang.Number periodSeconds;
        private final java.lang.Number successThreshold;
        private final imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeTcpSocket tcpSocket;
        private final java.lang.Number timeoutSeconds;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.exec = software.amazon.jsii.Kernel.get(this, "exec", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeExec.class));
            this.failureThreshold = software.amazon.jsii.Kernel.get(this, "failureThreshold", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.grpc = software.amazon.jsii.Kernel.get(this, "grpc", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeGrpc.class));
            this.httpGet = software.amazon.jsii.Kernel.get(this, "httpGet", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeHttpGet.class));
            this.initialDelaySeconds = software.amazon.jsii.Kernel.get(this, "initialDelaySeconds", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.periodSeconds = software.amazon.jsii.Kernel.get(this, "periodSeconds", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.successThreshold = software.amazon.jsii.Kernel.get(this, "successThreshold", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.tcpSocket = software.amazon.jsii.Kernel.get(this, "tcpSocket", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeTcpSocket.class));
            this.timeoutSeconds = software.amazon.jsii.Kernel.get(this, "timeoutSeconds", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.exec = builder.exec;
            this.failureThreshold = builder.failureThreshold;
            this.grpc = builder.grpc;
            this.httpGet = builder.httpGet;
            this.initialDelaySeconds = builder.initialDelaySeconds;
            this.periodSeconds = builder.periodSeconds;
            this.successThreshold = builder.successThreshold;
            this.tcpSocket = builder.tcpSocket;
            this.timeoutSeconds = builder.timeoutSeconds;
        }

        @Override
        public final imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeExec getExec() {
            return this.exec;
        }

        @Override
        public final java.lang.Number getFailureThreshold() {
            return this.failureThreshold;
        }

        @Override
        public final imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeGrpc getGrpc() {
            return this.grpc;
        }

        @Override
        public final imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeHttpGet getHttpGet() {
            return this.httpGet;
        }

        @Override
        public final java.lang.Number getInitialDelaySeconds() {
            return this.initialDelaySeconds;
        }

        @Override
        public final java.lang.Number getPeriodSeconds() {
            return this.periodSeconds;
        }

        @Override
        public final java.lang.Number getSuccessThreshold() {
            return this.successThreshold;
        }

        @Override
        public final imports.dev.knative.serving.ServiceSpecTemplateSpecContainersReadinessProbeTcpSocket getTcpSocket() {
            return this.tcpSocket;
        }

        @Override
        public final java.lang.Number getTimeoutSeconds() {
            return this.timeoutSeconds;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getExec() != null) {
                data.set("exec", om.valueToTree(this.getExec()));
            }
            if (this.getFailureThreshold() != null) {
                data.set("failureThreshold", om.valueToTree(this.getFailureThreshold()));
            }
            if (this.getGrpc() != null) {
                data.set("grpc", om.valueToTree(this.getGrpc()));
            }
            if (this.getHttpGet() != null) {
                data.set("httpGet", om.valueToTree(this.getHttpGet()));
            }
            if (this.getInitialDelaySeconds() != null) {
                data.set("initialDelaySeconds", om.valueToTree(this.getInitialDelaySeconds()));
            }
            if (this.getPeriodSeconds() != null) {
                data.set("periodSeconds", om.valueToTree(this.getPeriodSeconds()));
            }
            if (this.getSuccessThreshold() != null) {
                data.set("successThreshold", om.valueToTree(this.getSuccessThreshold()));
            }
            if (this.getTcpSocket() != null) {
                data.set("tcpSocket", om.valueToTree(this.getTcpSocket()));
            }
            if (this.getTimeoutSeconds() != null) {
                data.set("timeoutSeconds", om.valueToTree(this.getTimeoutSeconds()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ServiceSpecTemplateSpecContainersReadinessProbe"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ServiceSpecTemplateSpecContainersReadinessProbe.Jsii$Proxy that = (ServiceSpecTemplateSpecContainersReadinessProbe.Jsii$Proxy) o;

            if (this.exec != null ? !this.exec.equals(that.exec) : that.exec != null) return false;
            if (this.failureThreshold != null ? !this.failureThreshold.equals(that.failureThreshold) : that.failureThreshold != null) return false;
            if (this.grpc != null ? !this.grpc.equals(that.grpc) : that.grpc != null) return false;
            if (this.httpGet != null ? !this.httpGet.equals(that.httpGet) : that.httpGet != null) return false;
            if (this.initialDelaySeconds != null ? !this.initialDelaySeconds.equals(that.initialDelaySeconds) : that.initialDelaySeconds != null) return false;
            if (this.periodSeconds != null ? !this.periodSeconds.equals(that.periodSeconds) : that.periodSeconds != null) return false;
            if (this.successThreshold != null ? !this.successThreshold.equals(that.successThreshold) : that.successThreshold != null) return false;
            if (this.tcpSocket != null ? !this.tcpSocket.equals(that.tcpSocket) : that.tcpSocket != null) return false;
            return this.timeoutSeconds != null ? this.timeoutSeconds.equals(that.timeoutSeconds) : that.timeoutSeconds == null;
        }

        @Override
        public final int hashCode() {
            int result = this.exec != null ? this.exec.hashCode() : 0;
            result = 31 * result + (this.failureThreshold != null ? this.failureThreshold.hashCode() : 0);
            result = 31 * result + (this.grpc != null ? this.grpc.hashCode() : 0);
            result = 31 * result + (this.httpGet != null ? this.httpGet.hashCode() : 0);
            result = 31 * result + (this.initialDelaySeconds != null ? this.initialDelaySeconds.hashCode() : 0);
            result = 31 * result + (this.periodSeconds != null ? this.periodSeconds.hashCode() : 0);
            result = 31 * result + (this.successThreshold != null ? this.successThreshold.hashCode() : 0);
            result = 31 * result + (this.tcpSocket != null ? this.tcpSocket.hashCode() : 0);
            result = 31 * result + (this.timeoutSeconds != null ? this.timeoutSeconds.hashCode() : 0);
            return result;
        }
    }
}
