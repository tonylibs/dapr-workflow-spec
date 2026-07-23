package imports.io.dapr;

/**
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.465Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.ResiliencySpecTargetsComponents")
@software.amazon.jsii.Jsii.Proxy(ResiliencySpecTargetsComponents.Jsii$Proxy.class)
public interface ResiliencySpecTargetsComponents extends software.amazon.jsii.JsiiSerializable {

    /**
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ResiliencySpecTargetsComponentsInbound getInbound() {
        return null;
    }

    /**
     */
    default @org.jetbrains.annotations.Nullable imports.io.dapr.ResiliencySpecTargetsComponentsOutbound getOutbound() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link ResiliencySpecTargetsComponents}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link ResiliencySpecTargetsComponents}
     */
    public static final class Builder implements software.amazon.jsii.Builder<ResiliencySpecTargetsComponents> {
        imports.io.dapr.ResiliencySpecTargetsComponentsInbound inbound;
        imports.io.dapr.ResiliencySpecTargetsComponentsOutbound outbound;

        /**
         * Sets the value of {@link ResiliencySpecTargetsComponents#getInbound}
         * @param inbound the value to be set.
         * @return {@code this}
         */
        public Builder inbound(imports.io.dapr.ResiliencySpecTargetsComponentsInbound inbound) {
            this.inbound = inbound;
            return this;
        }

        /**
         * Sets the value of {@link ResiliencySpecTargetsComponents#getOutbound}
         * @param outbound the value to be set.
         * @return {@code this}
         */
        public Builder outbound(imports.io.dapr.ResiliencySpecTargetsComponentsOutbound outbound) {
            this.outbound = outbound;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link ResiliencySpecTargetsComponents}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public ResiliencySpecTargetsComponents build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link ResiliencySpecTargetsComponents}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements ResiliencySpecTargetsComponents {
        private final imports.io.dapr.ResiliencySpecTargetsComponentsInbound inbound;
        private final imports.io.dapr.ResiliencySpecTargetsComponentsOutbound outbound;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.inbound = software.amazon.jsii.Kernel.get(this, "inbound", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ResiliencySpecTargetsComponentsInbound.class));
            this.outbound = software.amazon.jsii.Kernel.get(this, "outbound", software.amazon.jsii.NativeType.forClass(imports.io.dapr.ResiliencySpecTargetsComponentsOutbound.class));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.inbound = builder.inbound;
            this.outbound = builder.outbound;
        }

        @Override
        public final imports.io.dapr.ResiliencySpecTargetsComponentsInbound getInbound() {
            return this.inbound;
        }

        @Override
        public final imports.io.dapr.ResiliencySpecTargetsComponentsOutbound getOutbound() {
            return this.outbound;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getInbound() != null) {
                data.set("inbound", om.valueToTree(this.getInbound()));
            }
            if (this.getOutbound() != null) {
                data.set("outbound", om.valueToTree(this.getOutbound()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("iodapr.ResiliencySpecTargetsComponents"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ResiliencySpecTargetsComponents.Jsii$Proxy that = (ResiliencySpecTargetsComponents.Jsii$Proxy) o;

            if (this.inbound != null ? !this.inbound.equals(that.inbound) : that.inbound != null) return false;
            return this.outbound != null ? this.outbound.equals(that.outbound) : that.outbound == null;
        }

        @Override
        public final int hashCode() {
            int result = this.inbound != null ? this.inbound.hashCode() : 0;
            result = 31 * result + (this.outbound != null ? this.outbound.hashCode() : 0);
            return result;
        }
    }
}
