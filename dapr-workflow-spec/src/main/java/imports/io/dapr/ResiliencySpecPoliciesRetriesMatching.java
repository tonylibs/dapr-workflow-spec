package imports.io.dapr;

/**
 * RetryMatching represents the rules to trigger retry in specific scenarios.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.464Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ResiliencySpecPoliciesRetriesMatching")
@software.amazon.jsii.Jsii.Proxy(ResiliencySpecPoliciesRetriesMatching.Jsii$Proxy.class)
public interface ResiliencySpecPoliciesRetriesMatching extends software.amazon.jsii.JsiiSerializable {

    /**
     * GRPCStatusCodes represents gRPC status codes to be retried.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getGRpcStatusCodes() {
        return null;
    }

    /**
     * HTTPStatusCodes represents HTTP status codes to be retried.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getHttpStatusCodes() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ResiliencySpecPoliciesRetriesMatching}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ResiliencySpecPoliciesRetriesMatching}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ResiliencySpecPoliciesRetriesMatching> {
        java.lang.String gRpcStatusCodes;
        java.lang.String httpStatusCodes;

        /**
         * Sets the value of {@link ResiliencySpecPoliciesRetriesMatching#getGRpcStatusCodes}
         * @param gRpcStatusCodes GRPCStatusCodes represents gRPC status codes to be retried.
         * @return {@code this}
         */
        public Builder gRpcStatusCodes(java.lang.String gRpcStatusCodes) {
            this.gRpcStatusCodes = gRpcStatusCodes;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpecPoliciesRetriesMatching#getHttpStatusCodes}
         * @param httpStatusCodes HTTPStatusCodes represents HTTP status codes to be retried.
         * @return {@code this}
         */
        public Builder httpStatusCodes(java.lang.String httpStatusCodes) {
            this.httpStatusCodes = httpStatusCodes;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ResiliencySpecPoliciesRetriesMatching}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ResiliencySpecPoliciesRetriesMatching build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ResiliencySpecPoliciesRetriesMatching}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ResiliencySpecPoliciesRetriesMatching {
        private final java.lang.String gRpcStatusCodes;
        private final java.lang.String httpStatusCodes;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.gRpcStatusCodes = software.amazon.jsii.Kernel.get(this, "gRpcStatusCodes", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.httpStatusCodes = software.amazon.jsii.Kernel.get(this, "httpStatusCodes", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.gRpcStatusCodes = builder.gRpcStatusCodes;
            this.httpStatusCodes = builder.httpStatusCodes;
        }

        @Override
        public final java.lang.String getGRpcStatusCodes() {
            return this.gRpcStatusCodes;
        }

        @Override
        public final java.lang.String getHttpStatusCodes() {
            return this.httpStatusCodes;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getGRpcStatusCodes() != null) {
                data.set("gRpcStatusCodes", om.valueToTree(this.getGRpcStatusCodes()));
            }
            if (this.getHttpStatusCodes() != null) {
                data.set("httpStatusCodes", om.valueToTree(this.getHttpStatusCodes()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ResiliencySpecPoliciesRetriesMatching"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ResiliencySpecPoliciesRetriesMatching.Jsii$Proxy that = (ResiliencySpecPoliciesRetriesMatching.Jsii$Proxy) o;

            if (this.gRpcStatusCodes != null ? !this.gRpcStatusCodes.equals(that.gRpcStatusCodes) : that.gRpcStatusCodes != null) return false;
            return this.httpStatusCodes != null ? this.httpStatusCodes.equals(that.httpStatusCodes) : that.httpStatusCodes == null;
        }

        @Override
        public final int hashCode() {
            int result = this.gRpcStatusCodes != null ? this.gRpcStatusCodes.hashCode() : 0;
            result = 31 * result + (this.httpStatusCodes != null ? this.httpStatusCodes.hashCode() : 0);
            return result;
        }
    }
}
