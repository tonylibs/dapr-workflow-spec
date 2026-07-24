package imports.dev.knative.internal.networking;

/**
 * HTTPIngressPath associates a path regex with a backend.
 * <p>
 * Incoming URLs matching
 * the path are forwarded to the backend.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:14.128Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.networking.$Module.class, fqn = "devknativeinternalnetworking.IngressSpecRulesHttpPaths")
@software.amazon.jsii.Jsii.Proxy(IngressSpecRulesHttpPaths.Jsii$Proxy.class)
public interface IngressSpecRulesHttpPaths extends software.amazon.jsii.JsiiSerializable {

    /**
     * Splits defines the referenced service endpoints to which the traffic will be forwarded to.
     */
    @org.jetbrains.annotations.NotNull java.util.List<imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplits> getSplits();

    /**
     * AppendHeaders allow specifying additional HTTP headers to add before forwarding a request to the destination service.
     * <p>
     * NOTE: This differs from K8s Ingress which doesn't allow header appending.
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, java.lang.String> getAppendHeaders() {
        return null;
    }

    /**
     * Headers defines header matching rules which is a map from a header name to HeaderMatch which specify a matching condition.
     * <p>
     * When a request matched with all the header matching rules,
     * the request is routed by the corresponding ingress rule.
     * If it is empty, the headers are not used for matching
     */
    default @org.jetbrains.annotations.Nullable java.util.Map<java.lang.String, imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsHeaders> getHeaders() {
        return null;
    }

    /**
     * Path represents a literal prefix to which this rule should apply.
     * <p>
     * Currently it can contain characters disallowed from the conventional
     * "path" part of a URL as defined by RFC 3986. Paths must begin with
     * a '/'. If unspecified, the path defaults to a catch all sending
     * traffic to the backend.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getPath() {
        return null;
    }

    /**
     * RewriteHost rewrites the incoming request's host header.
     * <p>
     * This field is currently experimental and not supported by all Ingress
     * implementations.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getRewriteHost() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link IngressSpecRulesHttpPaths}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link IngressSpecRulesHttpPaths}
     */
    public static final class Builder implements software.amazon.jsii.Builder<IngressSpecRulesHttpPaths> {
        java.util.List<imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplits> splits;
        java.util.Map<java.lang.String, java.lang.String> appendHeaders;
        java.util.Map<java.lang.String, imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsHeaders> headers;
        java.lang.String path;
        java.lang.String rewriteHost;

        /**
         * Sets the value of {@link IngressSpecRulesHttpPaths#getSplits}
         * @param splits Splits defines the referenced service endpoints to which the traffic will be forwarded to. This parameter is required.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder splits(java.util.List<? extends imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplits> splits) {
            this.splits = (java.util.List<imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplits>)splits;
            return this;
        }

        /**
         * Sets the value of {@link IngressSpecRulesHttpPaths#getAppendHeaders}
         * @param appendHeaders AppendHeaders allow specifying additional HTTP headers to add before forwarding a request to the destination service.
         *                      NOTE: This differs from K8s Ingress which doesn't allow header appending.
         * @return {@code this}
         */
        public Builder appendHeaders(java.util.Map<java.lang.String, java.lang.String> appendHeaders) {
            this.appendHeaders = appendHeaders;
            return this;
        }

        /**
         * Sets the value of {@link IngressSpecRulesHttpPaths#getHeaders}
         * @param headers Headers defines header matching rules which is a map from a header name to HeaderMatch which specify a matching condition.
         *                When a request matched with all the header matching rules,
         *                the request is routed by the corresponding ingress rule.
         *                If it is empty, the headers are not used for matching
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder headers(java.util.Map<java.lang.String, ? extends imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsHeaders> headers) {
            this.headers = (java.util.Map<java.lang.String, imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsHeaders>)headers;
            return this;
        }

        /**
         * Sets the value of {@link IngressSpecRulesHttpPaths#getPath}
         * @param path Path represents a literal prefix to which this rule should apply.
         *             Currently it can contain characters disallowed from the conventional
         *             "path" part of a URL as defined by RFC 3986. Paths must begin with
         *             a '/'. If unspecified, the path defaults to a catch all sending
         *             traffic to the backend.
         * @return {@code this}
         */
        public Builder path(java.lang.String path) {
            this.path = path;
            return this;
        }

        /**
         * Sets the value of {@link IngressSpecRulesHttpPaths#getRewriteHost}
         * @param rewriteHost RewriteHost rewrites the incoming request's host header.
         *                    This field is currently experimental and not supported by all Ingress
         *                    implementations.
         * @return {@code this}
         */
        public Builder rewriteHost(java.lang.String rewriteHost) {
            this.rewriteHost = rewriteHost;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link IngressSpecRulesHttpPaths}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public IngressSpecRulesHttpPaths build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link IngressSpecRulesHttpPaths}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements IngressSpecRulesHttpPaths {
        private final java.util.List<imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplits> splits;
        private final java.util.Map<java.lang.String, java.lang.String> appendHeaders;
        private final java.util.Map<java.lang.String, imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsHeaders> headers;
        private final java.lang.String path;
        private final java.lang.String rewriteHost;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.splits = software.amazon.jsii.Kernel.get(this, "splits", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplits.class)));
            this.appendHeaders = software.amazon.jsii.Kernel.get(this, "appendHeaders", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.headers = software.amazon.jsii.Kernel.get(this, "headers", software.amazon.jsii.NativeType.mapOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsHeaders.class)));
            this.path = software.amazon.jsii.Kernel.get(this, "path", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.rewriteHost = software.amazon.jsii.Kernel.get(this, "rewriteHost", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.splits = (java.util.List<imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplits>)java.util.Objects.requireNonNull(builder.splits, "splits is required");
            this.appendHeaders = builder.appendHeaders;
            this.headers = (java.util.Map<java.lang.String, imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsHeaders>)builder.headers;
            this.path = builder.path;
            this.rewriteHost = builder.rewriteHost;
        }

        @Override
        public final java.util.List<imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplits> getSplits() {
            return this.splits;
        }

        @Override
        public final java.util.Map<java.lang.String, java.lang.String> getAppendHeaders() {
            return this.appendHeaders;
        }

        @Override
        public final java.util.Map<java.lang.String, imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsHeaders> getHeaders() {
            return this.headers;
        }

        @Override
        public final java.lang.String getPath() {
            return this.path;
        }

        @Override
        public final java.lang.String getRewriteHost() {
            return this.rewriteHost;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            data.set("splits", om.valueToTree(this.getSplits()));
            if (this.getAppendHeaders() != null) {
                data.set("appendHeaders", om.valueToTree(this.getAppendHeaders()));
            }
            if (this.getHeaders() != null) {
                data.set("headers", om.valueToTree(this.getHeaders()));
            }
            if (this.getPath() != null) {
                data.set("path", om.valueToTree(this.getPath()));
            }
            if (this.getRewriteHost() != null) {
                data.set("rewriteHost", om.valueToTree(this.getRewriteHost()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeinternalnetworking.IngressSpecRulesHttpPaths"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            IngressSpecRulesHttpPaths.Jsii$Proxy that = (IngressSpecRulesHttpPaths.Jsii$Proxy) o;

            if (!splits.equals(that.splits)) return false;
            if (this.appendHeaders != null ? !this.appendHeaders.equals(that.appendHeaders) : that.appendHeaders != null) return false;
            if (this.headers != null ? !this.headers.equals(that.headers) : that.headers != null) return false;
            if (this.path != null ? !this.path.equals(that.path) : that.path != null) return false;
            return this.rewriteHost != null ? this.rewriteHost.equals(that.rewriteHost) : that.rewriteHost == null;
        }

        @Override
        public final int hashCode() {
            int result = this.splits.hashCode();
            result = 31 * result + (this.appendHeaders != null ? this.appendHeaders.hashCode() : 0);
            result = 31 * result + (this.headers != null ? this.headers.hashCode() : 0);
            result = 31 * result + (this.path != null ? this.path.hashCode() : 0);
            result = 31 * result + (this.rewriteHost != null ? this.rewriteHost.hashCode() : 0);
            return result;
        }
    }
}
