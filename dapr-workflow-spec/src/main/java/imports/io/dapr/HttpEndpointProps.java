package imports.io.dapr;

/**
 * HTTPEndpoint describes a Dapr HTTPEndpoint type for external service invocation.
 * <p>
 * This endpoint can be external to Dapr, or external to the environment.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.452Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.HttpEndpointProps")
@software.amazon.jsii.Jsii.Proxy(HttpEndpointProps.Jsii$Proxy.class)
public interface HttpEndpointProps extends software.amazon.jsii.JsiiSerializable {

    /**
     * Auth represents authentication details for the component.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.HttpEndpointAuth getAuth() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable org.cdk8s.ApiObjectMetadata getMetadata() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getScopes() {
        return null;
    }

    /**
     * HTTPEndpointSpec describes an access specification for allowing external service invocations.
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.HttpEndpointSpec getSpec() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link HttpEndpointProps}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link HttpEndpointProps}
     */
    public static final class Builder implements software.amazon.jsii.Builder<HttpEndpointProps> {
        imports.io.dapr.HttpEndpointAuth auth;
        org.cdk8s.ApiObjectMetadata metadata;
        java.util.List<java.lang.String> scopes;
        imports.io.dapr.HttpEndpointSpec spec;

        /**
         * Sets the value of {@link HttpEndpointProps#getAuth}
         * @param auth Auth represents authentication details for the component.
         * @return {@code this}
         */
        public Builder auth(imports.io.dapr.HttpEndpointAuth auth) {
            this.auth = auth;
            return this;
        }

        /**
         * Sets the value of {@link HttpEndpointProps#getMetadata}
         * @param metadata the value to be set.
         * @return {@code this}
         */
        public Builder metadata(org.cdk8s.ApiObjectMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Sets the value of {@link HttpEndpointProps#getScopes}
         * @param scopes the value to be set.
         * @return {@code this}
         */
        public Builder scopes(java.util.List<java.lang.String> scopes) {
            this.scopes = scopes;
            return this;
        }

        /**
         * Sets the value of {@link HttpEndpointProps#getSpec}
         * @param spec HTTPEndpointSpec describes an access specification for allowing external service invocations.
         * @return {@code this}
         */
        public Builder spec(imports.io.dapr.HttpEndpointSpec spec) {
            this.spec = spec;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link HttpEndpointProps}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public HttpEndpointProps build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link HttpEndpointProps}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements HttpEndpointProps {
        private final imports.io.dapr.HttpEndpointAuth auth;
        private final org.cdk8s.ApiObjectMetadata metadata;
        private final java.util.List<java.lang.String> scopes;
        private final imports.io.dapr.HttpEndpointSpec spec;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.auth = software.amazon.jsii.Kernel.get(this, "auth", software.amazon.jsii.NativeType.forClass(imports.io.dapr.HttpEndpointAuth.class));
            this.metadata = software.amazon.jsii.Kernel.get(this, "metadata", software.amazon.jsii.NativeType.forClass(org.cdk8s.ApiObjectMetadata.class));
            this.scopes = software.amazon.jsii.Kernel.get(this, "scopes", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.spec = software.amazon.jsii.Kernel.get(this, "spec", software.amazon.jsii.NativeType.forClass(imports.io.dapr.HttpEndpointSpec.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.auth = builder.auth;
            this.metadata = builder.metadata;
            this.scopes = builder.scopes;
            this.spec = builder.spec;
        }

        @Override
        public final imports.io.dapr.HttpEndpointAuth getAuth() {
            return this.auth;
        }

        @Override
        public final org.cdk8s.ApiObjectMetadata getMetadata() {
            return this.metadata;
        }

        @Override
        public final java.util.List<java.lang.String> getScopes() {
            return this.scopes;
        }

        @Override
        public final imports.io.dapr.HttpEndpointSpec getSpec() {
            return this.spec;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getAuth() != null) {
                data.set("auth", om.valueToTree(this.getAuth()));
            }
            if (this.getMetadata() != null) {
                data.set("metadata", om.valueToTree(this.getMetadata()));
            }
            if (this.getScopes() != null) {
                data.set("scopes", om.valueToTree(this.getScopes()));
            }
            if (this.getSpec() != null) {
                data.set("spec", om.valueToTree(this.getSpec()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.HttpEndpointProps"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            HttpEndpointProps.Jsii$Proxy that = (HttpEndpointProps.Jsii$Proxy) o;

            if (this.auth != null ? !this.auth.equals(that.auth) : that.auth != null) return false;
            if (this.metadata != null ? !this.metadata.equals(that.metadata) : that.metadata != null) return false;
            if (this.scopes != null ? !this.scopes.equals(that.scopes) : that.scopes != null) return false;
            return this.spec != null ? this.spec.equals(that.spec) : that.spec == null;
        }

        @Override
        public final int hashCode() {
            int result = this.auth != null ? this.auth.hashCode() : 0;
            result = 31 * result + (this.metadata != null ? this.metadata.hashCode() : 0);
            result = 31 * result + (this.scopes != null ? this.scopes.hashCode() : 0);
            result = 31 * result + (this.spec != null ? this.spec.hashCode() : 0);
            return result;
        }
    }
}
