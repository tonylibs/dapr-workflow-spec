package imports.dev.knative.serving;

/**
 * HTTPGet specifies an HTTP GET request to perform.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.130Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet.Jsii$Proxy.class)
public interface ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet extends software.amazon.jsii.JsiiSerializable {

    /**
     * Host name to connect to, defaults to the pod IP.
     * <p>
     * You probably want to set
     * "Host" in httpHeaders instead.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getHost() {
        return null;
    }

    /**
     * Custom headers to set in the request.
     * <p>
     * HTTP allows repeated headers.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGetHttpHeaders> getHttpHeaders() {
        return null;
    }

    /**
     * Path to access on the HTTP server.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getPath() {
        return null;
    }

    /**
     * Name or number of the port to access on the container.
     * <p>
     * Number must be in the range 1 to 65535.
     * Name must be an IANA_SVC_NAME.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGetPort getPort() {
        return null;
    }

    /**
     * Scheme to use for connecting to the host.
     * <p>
     * Defaults to HTTP.
     * <p>
     * Default: HTTP.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getScheme() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet> {
        java.lang.String host;
        java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGetHttpHeaders> httpHeaders;
        java.lang.String path;
        imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGetPort port;
        java.lang.String scheme;

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet#getHost}
         * @param host Host name to connect to, defaults to the pod IP.
         *             You probably want to set
         *             "Host" in httpHeaders instead.
         * @return {@code this}
         */
        public Builder host(java.lang.String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet#getHttpHeaders}
         * @param httpHeaders Custom headers to set in the request.
         *                    HTTP allows repeated headers.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder httpHeaders(java.util.List<? extends imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGetHttpHeaders> httpHeaders) {
            this.httpHeaders = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGetHttpHeaders>)httpHeaders;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet#getPath}
         * @param path Path to access on the HTTP server.
         * @return {@code this}
         */
        public Builder path(java.lang.String path) {
            this.path = path;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet#getPort}
         * @param port Name or number of the port to access on the container.
         *             Number must be in the range 1 to 65535.
         *             Name must be an IANA_SVC_NAME.
         * @return {@code this}
         */
        public Builder port(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGetPort port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet#getScheme}
         * @param scheme Scheme to use for connecting to the host.
         *               Defaults to HTTP.
         * @return {@code this}
         */
        public Builder scheme(java.lang.String scheme) {
            this.scheme = scheme;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet {
        private final java.lang.String host;
        private final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGetHttpHeaders> httpHeaders;
        private final java.lang.String path;
        private final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGetPort port;
        private final java.lang.String scheme;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.host = software.amazon.jsii.Kernel.get(this, "host", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.httpHeaders = software.amazon.jsii.Kernel.get(this, "httpHeaders", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGetHttpHeaders.class)));
            this.path = software.amazon.jsii.Kernel.get(this, "path", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.port = software.amazon.jsii.Kernel.get(this, "port", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGetPort.class));
            this.scheme = software.amazon.jsii.Kernel.get(this, "scheme", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.host = builder.host;
            this.httpHeaders = (java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGetHttpHeaders>)builder.httpHeaders;
            this.path = builder.path;
            this.port = builder.port;
            this.scheme = builder.scheme;
        }

        @Override
        public final java.lang.String getHost() {
            return this.host;
        }

        @Override
        public final java.util.List<imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGetHttpHeaders> getHttpHeaders() {
            return this.httpHeaders;
        }

        @Override
        public final java.lang.String getPath() {
            return this.path;
        }

        @Override
        public final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGetPort getPort() {
            return this.port;
        }

        @Override
        public final java.lang.String getScheme() {
            return this.scheme;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getHost() != null) {
                data.set("host", om.valueToTree(this.getHost()));
            }
            if (this.getHttpHeaders() != null) {
                data.set("httpHeaders", om.valueToTree(this.getHttpHeaders()));
            }
            if (this.getPath() != null) {
                data.set("path", om.valueToTree(this.getPath()));
            }
            if (this.getPort() != null) {
                data.set("port", om.valueToTree(this.getPort()));
            }
            if (this.getScheme() != null) {
                data.set("scheme", om.valueToTree(this.getScheme()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet.Jsii$Proxy that = (ConfigurationSpecTemplateSpecContainersStartupProbeHttpGet.Jsii$Proxy) o;

            if (this.host != null ? !this.host.equals(that.host) : that.host != null) return false;
            if (this.httpHeaders != null ? !this.httpHeaders.equals(that.httpHeaders) : that.httpHeaders != null) return false;
            if (this.path != null ? !this.path.equals(that.path) : that.path != null) return false;
            if (this.port != null ? !this.port.equals(that.port) : that.port != null) return false;
            return this.scheme != null ? this.scheme.equals(that.scheme) : that.scheme == null;
        }

        @Override
        public final int hashCode() {
            int result = this.host != null ? this.host.hashCode() : 0;
            result = 31 * result + (this.httpHeaders != null ? this.httpHeaders.hashCode() : 0);
            result = 31 * result + (this.path != null ? this.path.hashCode() : 0);
            result = 31 * result + (this.port != null ? this.port.hashCode() : 0);
            result = 31 * result + (this.scheme != null ? this.scheme.hashCode() : 0);
            return result;
        }
    }
}
