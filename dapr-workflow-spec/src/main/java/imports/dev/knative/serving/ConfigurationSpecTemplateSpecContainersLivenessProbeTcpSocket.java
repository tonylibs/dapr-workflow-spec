package imports.dev.knative.serving;

/**
 * TCPSocket specifies a connection to a TCP port.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.117Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocket")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocket.Jsii$Proxy.class)
public interface ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocket extends software.amazon.jsii.JsiiSerializable {

    /**
     * Optional: Host name to connect to, defaults to the pod IP.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getHost() {
        return null;
    }

    /**
     * Number or name of the port to access on the container.
     * <p>
     * Number must be in the range 1 to 65535.
     * Name must be an IANA_SVC_NAME.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocketPort getPort() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocket}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocket}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocket> {
        java.lang.String host;
        imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocketPort port;

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocket#getHost}
         * @param host Optional: Host name to connect to, defaults to the pod IP.
         * @return {@code this}
         */
        public Builder host(java.lang.String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocket#getPort}
         * @param port Number or name of the port to access on the container.
         *             Number must be in the range 1 to 65535.
         *             Name must be an IANA_SVC_NAME.
         * @return {@code this}
         */
        public Builder port(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocketPort port) {
            this.port = port;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocket}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocket build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocket}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocket {
        private final java.lang.String host;
        private final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocketPort port;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.host = software.amazon.jsii.Kernel.get(this, "host", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.port = software.amazon.jsii.Kernel.get(this, "port", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocketPort.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.host = builder.host;
            this.port = builder.port;
        }

        @Override
        public final java.lang.String getHost() {
            return this.host;
        }

        @Override
        public final imports.dev.knative.serving.ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocketPort getPort() {
            return this.port;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getHost() != null) {
                data.set("host", om.valueToTree(this.getHost()));
            }
            if (this.getPort() != null) {
                data.set("port", om.valueToTree(this.getPort()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocket"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocket.Jsii$Proxy that = (ConfigurationSpecTemplateSpecContainersLivenessProbeTcpSocket.Jsii$Proxy) o;

            if (this.host != null ? !this.host.equals(that.host) : that.host != null) return false;
            return this.port != null ? this.port.equals(that.port) : that.port == null;
        }

        @Override
        public final int hashCode() {
            int result = this.host != null ? this.host.hashCode() : 0;
            result = 31 * result + (this.port != null ? this.port.hashCode() : 0);
            return result;
        }
    }
}
