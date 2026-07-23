package imports.dev.knative.internal.networking;

/**
 * IngressRule represents the rules mapping the paths under a specified host to the related backend services.
 * <p>
 * Incoming requests are first evaluated for a host
 * match, then routed to the backend associated with the matching IngressRuleValue.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:14.122Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.networking.$Module.class, fqn = "devknativeinternalnetworking.IngressSpecRules")
@software.amazon.jsii.Jsii.Proxy(IngressSpecRules.Jsii$Proxy.class)
public interface IngressSpecRules extends software.amazon.jsii.JsiiSerializable {

    /**
     * Host is the fully qualified domain name of a network host, as defined by RFC 3986.
     * <p>
     * Note the following deviations from the "host" part of the
     * URI as defined in the RFC:
     * <p>
     * <ol>
     * <li>IPs are not allowed. Currently a rule value can only apply to the
     * IP in the Spec of the parent .</li>
     * <li>The <code>:</code> delimiter is not respected because ports are not allowed.
     * Currently the port of an Ingress is implicitly :80 for http and
     * :443 for https.
     * Both these may change in the future.
     * If the host is unspecified, the Ingress routes all traffic based on the
     * specified IngressRuleValue.
     * If multiple matching Hosts were provided, the first rule will take precedent.</li>
     * </ol>
     */
    default @org.jetbrains.annotations.Nullable java.util.List<java.lang.String> getHosts() {
        return null;
    }

    /**
     * HTTP represents a rule to apply against incoming requests.
     * <p>
     * If the
     * rule is satisfied, the request is routed to the specified backend.
     */
    default @org.jetbrains.annotations.Nullable imports.dev.knative.internal.networking.IngressSpecRulesHttp getHttp() {
        return null;
    }

    /**
     * Visibility signifies whether this rule should <code>ClusterLocal</code>.
     * <p>
     * If it's not
     * specified then it defaults to <code>ExternalIP</code>.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getVisibility() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link IngressSpecRules}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link IngressSpecRules}
     */
    public static final class Builder implements software.amazon.jsii.Builder<IngressSpecRules> {
        java.util.List<java.lang.String> hosts;
        imports.dev.knative.internal.networking.IngressSpecRulesHttp http;
        java.lang.String visibility;

        /**
         * Sets the value of {@link IngressSpecRules#getHosts}
         * @param hosts Host is the fully qualified domain name of a network host, as defined by RFC 3986.
         *              Note the following deviations from the "host" part of the
         *              URI as defined in the RFC:
         *              <p>
         *              <ol>
         *              <li>IPs are not allowed. Currently a rule value can only apply to the
         *              IP in the Spec of the parent .</li>
         *              <li>The <code>:</code> delimiter is not respected because ports are not allowed.
         *              Currently the port of an Ingress is implicitly :80 for http and
         *              :443 for https.
         *              Both these may change in the future.
         *              If the host is unspecified, the Ingress routes all traffic based on the
         *              specified IngressRuleValue.
         *              If multiple matching Hosts were provided, the first rule will take precedent.</li>
         *              </ol>
         * @return {@code this}
         */
        public Builder hosts(java.util.List<java.lang.String> hosts) {
            this.hosts = hosts;
            return this;
        }

        /**
         * Sets the value of {@link IngressSpecRules#getHttp}
         * @param http HTTP represents a rule to apply against incoming requests.
         *             If the
         *             rule is satisfied, the request is routed to the specified backend.
         * @return {@code this}
         */
        public Builder http(imports.dev.knative.internal.networking.IngressSpecRulesHttp http) {
            this.http = http;
            return this;
        }

        /**
         * Sets the value of {@link IngressSpecRules#getVisibility}
         * @param visibility Visibility signifies whether this rule should <code>ClusterLocal</code>.
         *                   If it's not
         *                   specified then it defaults to <code>ExternalIP</code>.
         * @return {@code this}
         */
        public Builder visibility(java.lang.String visibility) {
            this.visibility = visibility;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link IngressSpecRules}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public IngressSpecRules build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link IngressSpecRules}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements IngressSpecRules {
        private final java.util.List<java.lang.String> hosts;
        private final imports.dev.knative.internal.networking.IngressSpecRulesHttp http;
        private final java.lang.String visibility;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.hosts = software.amazon.jsii.Kernel.get(this, "hosts", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(java.lang.String.class)));
            this.http = software.amazon.jsii.Kernel.get(this, "http", software.amazon.jsii.NativeType.forClass(imports.dev.knative.internal.networking.IngressSpecRulesHttp.class));
            this.visibility = software.amazon.jsii.Kernel.get(this, "visibility", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.hosts = builder.hosts;
            this.http = builder.http;
            this.visibility = builder.visibility;
        }

        @Override
        public final java.util.List<java.lang.String> getHosts() {
            return this.hosts;
        }

        @Override
        public final imports.dev.knative.internal.networking.IngressSpecRulesHttp getHttp() {
            return this.http;
        }

        @Override
        public final java.lang.String getVisibility() {
            return this.visibility;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getHosts() != null) {
                data.set("hosts", om.valueToTree(this.getHosts()));
            }
            if (this.getHttp() != null) {
                data.set("http", om.valueToTree(this.getHttp()));
            }
            if (this.getVisibility() != null) {
                data.set("visibility", om.valueToTree(this.getVisibility()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeinternalnetworking.IngressSpecRules"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            IngressSpecRules.Jsii$Proxy that = (IngressSpecRules.Jsii$Proxy) o;

            if (this.hosts != null ? !this.hosts.equals(that.hosts) : that.hosts != null) return false;
            if (this.http != null ? !this.http.equals(that.http) : that.http != null) return false;
            return this.visibility != null ? this.visibility.equals(that.visibility) : that.visibility == null;
        }

        @Override
        public final int hashCode() {
            int result = this.hosts != null ? this.hosts.hashCode() : 0;
            result = 31 * result + (this.http != null ? this.http.hashCode() : 0);
            result = 31 * result + (this.visibility != null ? this.visibility.hashCode() : 0);
            return result;
        }
    }
}
