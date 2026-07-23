package imports.dev.knative.serving;

/**
 * TrafficTarget holds a single entry of the routing table for a Route.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.196Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ServiceSpecTraffic")
@software.amazon.jsii.Jsii.Proxy(ServiceSpecTraffic.Jsii$Proxy.class)
public interface ServiceSpecTraffic extends software.amazon.jsii.JsiiSerializable {

    /**
     * ConfigurationName of a configuration to whose latest revision we will send this portion of traffic.
     * <p>
     * When the "status.latestReadyRevisionName" of the
     * referenced configuration changes, we will automatically migrate traffic
     * from the prior "latest ready" revision to the new one.  This field is never
     * set in Route's status, only its spec.  This is mutually exclusive with
     * RevisionName.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getConfigurationName() {
        return null;
    }

    /**
     * LatestRevision may be optionally provided to indicate that the latest ready Revision of the Configuration should be used for this traffic target.
     * <p>
     * When provided LatestRevision must be true if RevisionName is
     * empty; it must be false when RevisionName is non-empty.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getLatestRevision() {
        return null;
    }

    /**
     * Percent indicates that percentage based routing should be used and the value indicates the percent of traffic that is be routed to this Revision or Configuration.
     * <p>
     * <code>0</code> (zero) mean no traffic, <code>100</code> means all
     * traffic.
     * When percentage based routing is being used the follow rules apply:
     * <p>
     * <ul>
     * <li>the sum of all percent values must equal 100</li>
     * <li>when not specified, the implied value for <code>percent</code> is zero for
     * that particular Revision or Configuration</li>
     * </ul>
     */
    default @org.jetbrains.annotations.Nullable java.lang.Number getPercent() {
        return null;
    }

    /**
     * RevisionName of a specific revision to which to send this portion of traffic.
     * <p>
     * This is mutually exclusive with ConfigurationName.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getRevisionName() {
        return null;
    }

    /**
     * Tag is optionally used to expose a dedicated url for referencing this target exclusively.
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getTag() {
        return null;
    }

    /**
     * URL displays the URL for accessing named traffic targets.
     * <p>
     * URL is displayed in
     * status, and is disallowed on spec. URL must contain a scheme (e.g. http://) and
     * a hostname, but may not contain anything else (e.g. basic auth, url path, etc.)
     */
    default @org.jetbrains.annotations.Nullable java.lang.String getUrl() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ServiceSpecTraffic}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ServiceSpecTraffic}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ServiceSpecTraffic> {
        java.lang.String configurationName;
        java.lang.Boolean latestRevision;
        java.lang.Number percent;
        java.lang.String revisionName;
        java.lang.String tag;
        java.lang.String url;

        /**
         * Sets the value of {@link ServiceSpecTraffic#getConfigurationName}
         * @param configurationName ConfigurationName of a configuration to whose latest revision we will send this portion of traffic.
         *                          When the "status.latestReadyRevisionName" of the
         *                          referenced configuration changes, we will automatically migrate traffic
         *                          from the prior "latest ready" revision to the new one.  This field is never
         *                          set in Route's status, only its spec.  This is mutually exclusive with
         *                          RevisionName.
         * @return {@code this}
         */
        public Builder configurationName(java.lang.String configurationName) {
            this.configurationName = configurationName;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTraffic#getLatestRevision}
         * @param latestRevision LatestRevision may be optionally provided to indicate that the latest ready Revision of the Configuration should be used for this traffic target.
         *                       When provided LatestRevision must be true if RevisionName is
         *                       empty; it must be false when RevisionName is non-empty.
         * @return {@code this}
         */
        public Builder latestRevision(java.lang.Boolean latestRevision) {
            this.latestRevision = latestRevision;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTraffic#getPercent}
         * @param percent Percent indicates that percentage based routing should be used and the value indicates the percent of traffic that is be routed to this Revision or Configuration.
         *                <code>0</code> (zero) mean no traffic, <code>100</code> means all
         *                traffic.
         *                When percentage based routing is being used the follow rules apply:
         *                <p>
         *                <ul>
         *                <li>the sum of all percent values must equal 100</li>
         *                <li>when not specified, the implied value for <code>percent</code> is zero for
         *                that particular Revision or Configuration</li>
         *                </ul>
         * @return {@code this}
         */
        public Builder percent(java.lang.Number percent) {
            this.percent = percent;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTraffic#getRevisionName}
         * @param revisionName RevisionName of a specific revision to which to send this portion of traffic.
         *                     This is mutually exclusive with ConfigurationName.
         * @return {@code this}
         */
        public Builder revisionName(java.lang.String revisionName) {
            this.revisionName = revisionName;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTraffic#getTag}
         * @param tag Tag is optionally used to expose a dedicated url for referencing this target exclusively.
         * @return {@code this}
         */
        public Builder tag(java.lang.String tag) {
            this.tag = tag;
            return this;
        }

        /**
         * Sets the value of {@link ServiceSpecTraffic#getUrl}
         * @param url URL displays the URL for accessing named traffic targets.
         *            URL is displayed in
         *            status, and is disallowed on spec. URL must contain a scheme (e.g. http://) and
         *            a hostname, but may not contain anything else (e.g. basic auth, url path, etc.)
         * @return {@code this}
         */
        public Builder url(java.lang.String url) {
            this.url = url;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ServiceSpecTraffic}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ServiceSpecTraffic build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ServiceSpecTraffic}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ServiceSpecTraffic {
        private final java.lang.String configurationName;
        private final java.lang.Boolean latestRevision;
        private final java.lang.Number percent;
        private final java.lang.String revisionName;
        private final java.lang.String tag;
        private final java.lang.String url;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.configurationName = software.amazon.jsii.Kernel.get(this, "configurationName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.latestRevision = software.amazon.jsii.Kernel.get(this, "latestRevision", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.percent = software.amazon.jsii.Kernel.get(this, "percent", software.amazon.jsii.NativeType.forClass(java.lang.Number.class));
            this.revisionName = software.amazon.jsii.Kernel.get(this, "revisionName", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.tag = software.amazon.jsii.Kernel.get(this, "tag", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
            this.url = software.amazon.jsii.Kernel.get(this, "url", software.amazon.jsii.NativeType.forClass(java.lang.String.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.configurationName = builder.configurationName;
            this.latestRevision = builder.latestRevision;
            this.percent = builder.percent;
            this.revisionName = builder.revisionName;
            this.tag = builder.tag;
            this.url = builder.url;
        }

        @Override
        public final java.lang.String getConfigurationName() {
            return this.configurationName;
        }

        @Override
        public final java.lang.Boolean getLatestRevision() {
            return this.latestRevision;
        }

        @Override
        public final java.lang.Number getPercent() {
            return this.percent;
        }

        @Override
        public final java.lang.String getRevisionName() {
            return this.revisionName;
        }

        @Override
        public final java.lang.String getTag() {
            return this.tag;
        }

        @Override
        public final java.lang.String getUrl() {
            return this.url;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getConfigurationName() != null) {
                data.set("configurationName", om.valueToTree(this.getConfigurationName()));
            }
            if (this.getLatestRevision() != null) {
                data.set("latestRevision", om.valueToTree(this.getLatestRevision()));
            }
            if (this.getPercent() != null) {
                data.set("percent", om.valueToTree(this.getPercent()));
            }
            if (this.getRevisionName() != null) {
                data.set("revisionName", om.valueToTree(this.getRevisionName()));
            }
            if (this.getTag() != null) {
                data.set("tag", om.valueToTree(this.getTag()));
            }
            if (this.getUrl() != null) {
                data.set("url", om.valueToTree(this.getUrl()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.ServiceSpecTraffic"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ServiceSpecTraffic.Jsii$Proxy that = (ServiceSpecTraffic.Jsii$Proxy) o;

            if (this.configurationName != null ? !this.configurationName.equals(that.configurationName) : that.configurationName != null) return false;
            if (this.latestRevision != null ? !this.latestRevision.equals(that.latestRevision) : that.latestRevision != null) return false;
            if (this.percent != null ? !this.percent.equals(that.percent) : that.percent != null) return false;
            if (this.revisionName != null ? !this.revisionName.equals(that.revisionName) : that.revisionName != null) return false;
            if (this.tag != null ? !this.tag.equals(that.tag) : that.tag != null) return false;
            return this.url != null ? this.url.equals(that.url) : that.url == null;
        }

        @Override
        public final int hashCode() {
            int result = this.configurationName != null ? this.configurationName.hashCode() : 0;
            result = 31 * result + (this.latestRevision != null ? this.latestRevision.hashCode() : 0);
            result = 31 * result + (this.percent != null ? this.percent.hashCode() : 0);
            result = 31 * result + (this.revisionName != null ? this.revisionName.hashCode() : 0);
            result = 31 * result + (this.tag != null ? this.tag.hashCode() : 0);
            result = 31 * result + (this.url != null ? this.url.hashCode() : 0);
            return result;
        }
    }
}
