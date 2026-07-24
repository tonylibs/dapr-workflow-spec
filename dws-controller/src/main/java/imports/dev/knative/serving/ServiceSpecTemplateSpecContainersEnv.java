package imports.dev.knative.serving;

/**
 * EnvVar represents an environment variable present in a Container.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.185Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ServiceSpecTemplateSpecContainersEnv")
@software.amazon.jsii.Jsii.Proxy(ServiceSpecTemplateSpecContainersEnv.Jsii$Proxy.class)
public interface ServiceSpecTemplateSpecContainersEnv extends software.amazon.jsii.JsiiSerializable {

    /**
     * Name of the environment variable.
     * <p>
     * May consist of any printable ASCII characters except '='.
     */
    @org.jetbrains.annotations.NotNull java.lang.String getName();

    /**
     * Variable references $(VAR_NAME) are expanded using the previously defined environment variables in the container and any service environment variables.
     * <p>
     * If a variable cannot be resolved,
     * the reference in the input string will be unchanged. Double $$ are reduced
     * to a single $, which allows for escaping the $(VAR_NAME) syntax: i.e.
     * "$$(VAR_NAME)" will produce the string literal "$(VAR_NAME)".
     * Escaped references will never be expanded, regardless of whether the variable
     * exists or not.
     * Defaults to "".
     * <p>
     * Default: .
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getValue() {
        return null;
    }

    /**
     * Source for the environment variable's value.
     * <p>
     * Cannot be used if value is not empty.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ServiceSpecTemplateSpecContainersEnvValueFrom getValueFrom() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ServiceSpecTemplateSpecContainersEnv}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ServiceSpecTemplateSpecContainersEnv}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ServiceSpecTemplateSpecContainersEnv> {
        java.lang.String name;
        java.lang.String value;
        imports.dev.knative.serving.ServiceSpecTemplateSpecContainersEnvValueFrom valueFrom;

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecContainersEnv#getName}
         * @param name Name of the environment variable. This parameter is required.
         *             May consist of any printable ASCII characters except '='.
         * @return {@code this}
         */
        public Builder name(java.lang.String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecContainersEnv#getValue}
         * @param value Variable references $(VAR_NAME) are expanded using the previously defined environment variables in the container and any service environment variables.
         *              If a variable cannot be resolved,
         *              the reference in the input string will be unchanged. Double $$ are reduced
         *              to a single $, which allows for escaping the $(VAR_NAME) syntax: i.e.
         *              "$$(VAR_NAME)" will produce the string literal "$(VAR_NAME)".
         *              Escaped references will never be expanded, regardless of whether the variable
         *              exists or not.
         *              Defaults to "".
         * @return {@code this}
         */
        public Builder value(java.lang.String value) {
            this.value = value;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTemplateSpecContainersEnv#getValueFrom}
         * @param valueFrom Source for the environment variable's value.
         *                  Cannot be used if value is not empty.
         * @return {@code this}
         */
        public Builder valueFrom(imports.dev.knative.serving.ServiceSpecTemplateSpecContainersEnvValueFrom valueFrom) {
            this.valueFrom = valueFrom;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ServiceSpecTemplateSpecContainersEnv}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ServiceSpecTemplateSpecContainersEnv build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ServiceSpecTemplateSpecContainersEnv}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ServiceSpecTemplateSpecContainersEnv {
        private final java.lang.String name;
        private final java.lang.String value;
        private final imports.dev.knative.serving.ServiceSpecTemplateSpecContainersEnvValueFrom valueFrom;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.name = software.amazon.jsii.Kernel.get(this, "name", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.value = software.amazon.jsii.Kernel.get(this, "value", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.valueFrom = software.amazon.jsii.Kernel.get(this, "valueFrom", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ServiceSpecTemplateSpecContainersEnvValueFrom.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.name = java.util.Objects.requireNonNull(builder.name, "name is required");
            this.value = builder.value;
            this.valueFrom = builder.valueFrom;
        }

        @Override
        public final java.lang.String getName() {
            return this.name;
        }

        @Override
        public final java.lang.String getValue() {
            return this.value;
        }

        @Override
        public final imports.dev.knative.serving.ServiceSpecTemplateSpecContainersEnvValueFrom getValueFrom() {
            return this.valueFrom;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("name", om.valueToTree(this.getName()));
            if (this.getValue() != null) {
                data.set("value", om.valueToTree(this.getValue()));
            }
            if (this.getValueFrom() != null) {
                data.set("valueFrom", om.valueToTree(this.getValueFrom()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ServiceSpecTemplateSpecContainersEnv"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ServiceSpecTemplateSpecContainersEnv.Jsii$Proxy that = (ServiceSpecTemplateSpecContainersEnv.Jsii$Proxy) o;

            if (!name.equals(that.name)) return false;
            if (this.value != null ? !this.value.equals(that.value) : that.value != null) return false;
            return this.valueFrom != null ? this.valueFrom.equals(that.valueFrom) : that.valueFrom == null;
        }

        @Override
        public final int hashCode() {
            int result = this.name.hashCode();
            result = 31 * result + (this.value != null ? this.value.hashCode() : 0);
            result = 31 * result + (this.valueFrom != null ? this.valueFrom.hashCode() : 0);
            return result;
        }
    }
}
