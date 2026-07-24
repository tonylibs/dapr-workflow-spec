package imports.dev.knative.serving;

/**
 * ContainerPort represents a network port in a single container.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.117Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ConfigurationSpecTemplateSpecContainersPorts")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecTemplateSpecContainersPorts.Jsii$Proxy.class)
public interface ConfigurationSpecTemplateSpecContainersPorts extends software.amazon.jsii.JsiiSerializable {

    /**
     * Number of port to expose on the pod's IP address.
     * <p>
     * This must be a valid port number, 0 &lt; x &lt; 65536.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getContainerPort() {
        return null;
    }

    /**
     * If specified, this must be an IANA_SVC_NAME and unique within the pod.
     * <p>
     * Each
     * named port in a pod must have a unique name. Name for the port that can be
     * referred to by services.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getName() {
        return null;
    }

    /**
     * Protocol for port.
     * <p>
     * Must be UDP, TCP, or SCTP.
     * Defaults to "TCP".
     * <p>
     * Default: TCP".
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getProtocol() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecTemplateSpecContainersPorts}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecTemplateSpecContainersPorts}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecTemplateSpecContainersPorts> {
        java.lang.Number containerPort;
        java.lang.String name;
        java.lang.String protocol;

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersPorts#getContainerPort}
         * @param containerPort Number of port to expose on the pod's IP address.
         *                      This must be a valid port number, 0 &lt; x &lt; 65536.
         * @return {@code this}
         */
        public Builder containerPort(java.lang.Number containerPort) {
            this.containerPort = containerPort;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersPorts#getName}
         * @param name If specified, this must be an IANA_SVC_NAME and unique within the pod.
         *             Each
         *             named port in a pod must have a unique name. Name for the port that can be
         *             referred to by services.
         * @return {@code this}
         */
        public Builder name(java.lang.String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecTemplateSpecContainersPorts#getProtocol}
         * @param protocol Protocol for port.
         *                 Must be UDP, TCP, or SCTP.
         *                 Defaults to "TCP".
         * @return {@code this}
         */
        public Builder protocol(java.lang.String protocol) {
            this.protocol = protocol;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecTemplateSpecContainersPorts}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecTemplateSpecContainersPorts build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecTemplateSpecContainersPorts}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecTemplateSpecContainersPorts {
        private final java.lang.Number containerPort;
        private final java.lang.String name;
        private final java.lang.String protocol;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.containerPort = software.amazon.jsii.Kernel.get(this, "containerPort", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.protocol = software.amazon.jsii.Kernel.get(this, "protocol", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.containerPort = builder.containerPort;
            this.name = builder.name;
            this.protocol = builder.protocol;
        }

        @Override
        public final java.lang.Number getContainerPort() {
            return this.containerPort;
        }

        @Override
        public final java.lang.String getName() {
            return this.name;
        }

        @Override
        public final java.lang.String getProtocol() {
            return this.protocol;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getContainerPort() != null) {
                data.set("containerPort", om.valueToTree(this.getContainerPort()));
            }
            if (this.getName() != null) {
                data.set("name", om.valueToTree(this.getName()));
            }
            if (this.getProtocol() != null) {
                data.set("protocol", om.valueToTree(this.getProtocol()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ConfigurationSpecTemplateSpecContainersPorts"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecTemplateSpecContainersPorts.Jsii$Proxy that = (ConfigurationSpecTemplateSpecContainersPorts.Jsii$Proxy) o;

            if (this.containerPort != null ? !this.containerPort.equals(that.containerPort) : that.containerPort != null) return false;
            if (this.name != null ? !this.name.equals(that.name) : that.name != null) return false;
            return this.protocol != null ? this.protocol.equals(that.protocol) : that.protocol == null;
        }

        @Override
        public final int hashCode() {
            int result = this.containerPort != null ? this.containerPort.hashCode() : 0;
            result = 31 * result + (this.name != null ? this.name.hashCode() : 0);
            result = 31 * result + (this.protocol != null ? this.protocol.hashCode() : 0);
            return result;
        }
    }
}
