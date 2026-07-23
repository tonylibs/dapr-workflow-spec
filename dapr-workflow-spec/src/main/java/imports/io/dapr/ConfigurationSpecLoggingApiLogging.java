package imports.io.dapr;

/**
 * Configure API logging.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.440Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ConfigurationSpecLoggingApiLogging")
@software.amazon.jsii.Jsii.Proxy(ConfigurationSpecLoggingApiLogging.Jsii$Proxy.class)
public interface ConfigurationSpecLoggingApiLogging extends software.amazon.jsii.JsiiSerializable {

    /**
     * Default value for enabling API logging.
     * <p>
     * Sidecars can always override this by setting <code>--enable-api-logging</code> to true or false explicitly.
     * The default value is false.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getEnabled() {
        return null;
    }

    /**
     * When enabled, obfuscates the values of URLs in HTTP API logs, logging the route name rather than the full path being invoked, which could contain PII.
     * <p>
     * Default: false.
     * This option has no effect if API logging is disabled.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getObfuscateUrLs() {
        return null;
    }

    /**
     * If true, health checks are not reported in API logs.
     * <p>
     * Default: false.
     * This option has no effect if API logging is disabled.
     */
    default @org.jetbrains.annotations.Nullable java.lang.Boolean getOmitHealthChecks() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ConfigurationSpecLoggingApiLogging}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ConfigurationSpecLoggingApiLogging}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ConfigurationSpecLoggingApiLogging> {
        java.lang.Boolean enabled;
        java.lang.Boolean obfuscateUrLs;
        java.lang.Boolean omitHealthChecks;

        /**
         * Sets the value of {@link ConfigurationSpecLoggingApiLogging#getEnabled}
         * @param enabled Default value for enabling API logging.
         *                Sidecars can always override this by setting <code>--enable-api-logging</code> to true or false explicitly.
         *                The default value is false.
         * @return {@code this}
         */
        public Builder enabled(java.lang.Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecLoggingApiLogging#getObfuscateUrLs}
         * @param obfuscateUrLs When enabled, obfuscates the values of URLs in HTTP API logs, logging the route name rather than the full path being invoked, which could contain PII.
         *                      Default: false.
         *                      This option has no effect if API logging is disabled.
         * @return {@code this}
         */
        public Builder obfuscateUrLs(java.lang.Boolean obfuscateUrLs) {
            this.obfuscateUrLs = obfuscateUrLs;
            return this;
        }

        /**
         * Sets the value of {@link ConfigurationSpecLoggingApiLogging#getOmitHealthChecks}
         * @param omitHealthChecks If true, health checks are not reported in API logs.
         *                         Default: false.
         *                         This option has no effect if API logging is disabled.
         * @return {@code this}
         */
        public Builder omitHealthChecks(java.lang.Boolean omitHealthChecks) {
            this.omitHealthChecks = omitHealthChecks;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ConfigurationSpecLoggingApiLogging}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ConfigurationSpecLoggingApiLogging build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ConfigurationSpecLoggingApiLogging}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ConfigurationSpecLoggingApiLogging {
        private final java.lang.Boolean enabled;
        private final java.lang.Boolean obfuscateUrLs;
        private final java.lang.Boolean omitHealthChecks;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.enabled = software.amazon.jsii.Kernel.get(this, "enabled", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.obfuscateUrLs = software.amazon.jsii.Kernel.get(this, "obfuscateUrLs", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
            this.omitHealthChecks = software.amazon.jsii.Kernel.get(this, "omitHealthChecks", software.amazon.jsii.NativeType.forClass(java.lang.Boolean.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.enabled = builder.enabled;
            this.obfuscateUrLs = builder.obfuscateUrLs;
            this.omitHealthChecks = builder.omitHealthChecks;
        }

        @Override
        public final java.lang.Boolean getEnabled() {
            return this.enabled;
        }

        @Override
        public final java.lang.Boolean getObfuscateUrLs() {
            return this.obfuscateUrLs;
        }

        @Override
        public final java.lang.Boolean getOmitHealthChecks() {
            return this.omitHealthChecks;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getEnabled() != null) {
                data.set("enabled", om.valueToTree(this.getEnabled()));
            }
            if (this.getObfuscateUrLs() != null) {
                data.set("obfuscateUrLs", om.valueToTree(this.getObfuscateUrLs()));
            }
            if (this.getOmitHealthChecks() != null) {
                data.set("omitHealthChecks", om.valueToTree(this.getOmitHealthChecks()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ConfigurationSpecLoggingApiLogging"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConfigurationSpecLoggingApiLogging.Jsii$Proxy that = (ConfigurationSpecLoggingApiLogging.Jsii$Proxy) o;

            if (this.enabled != null ? !this.enabled.equals(that.enabled) : that.enabled != null) return false;
            if (this.obfuscateUrLs != null ? !this.obfuscateUrLs.equals(that.obfuscateUrLs) : that.obfuscateUrLs != null) return false;
            return this.omitHealthChecks != null ? this.omitHealthChecks.equals(that.omitHealthChecks) : that.omitHealthChecks == null;
        }

        @Override
        public final int hashCode() {
            int result = this.enabled != null ? this.enabled.hashCode() : 0;
            result = 31 * result + (this.obfuscateUrLs != null ? this.obfuscateUrLs.hashCode() : 0);
            result = 31 * result + (this.omitHealthChecks != null ? this.omitHealthChecks.hashCode() : 0);
            return result;
        }
    }
}
