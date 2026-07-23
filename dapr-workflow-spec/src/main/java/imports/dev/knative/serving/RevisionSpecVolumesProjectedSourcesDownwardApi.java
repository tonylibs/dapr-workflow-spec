package imports.dev.knative.serving;

/**
 * downwardAPI information about the downwardAPI data to project.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.168Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.RevisionSpecVolumesProjectedSourcesDownwardApi")
@software.amazon.jsii.Jsii.Proxy(RevisionSpecVolumesProjectedSourcesDownwardApi.Jsii$Proxy.class)
public interface RevisionSpecVolumesProjectedSourcesDownwardApi extends software.amazon.jsii.JsiiSerializable {

    /**
     * Items is a list of DownwardAPIVolume file.
     */
    default @org.jetbrains.annotations.Nullable java.util.List<imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApiItems> getItems() {
        return null;
    }

    /**
     * @return a {@link Builder} of {@link RevisionSpecVolumesProjectedSourcesDownwardApi}
     */
    static Builder builder() {
        return new Builder();
    }
    /**
     * A builder for {@link RevisionSpecVolumesProjectedSourcesDownwardApi}
     */
    public static final class Builder implements software.amazon.jsii.Builder<RevisionSpecVolumesProjectedSourcesDownwardApi> {
        java.util.List<imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApiItems> items;

        /**
         * Sets the value of {@link RevisionSpecVolumesProjectedSourcesDownwardApi#getItems}
         * @param items Items is a list of DownwardAPIVolume file.
         * @return {@code this}
         */
        @SuppressWarnings("unchecked")
        public Builder items(java.util.List<? extends imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApiItems> items) {
            this.items = (java.util.List<imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApiItems>)items;
            return this;
        }

        /**
         * Builds the configured instance.
         * @return a new instance of {@link RevisionSpecVolumesProjectedSourcesDownwardApi}
         * @throws NullPointerException if any required attribute was not provided
         */
        @Override
        public RevisionSpecVolumesProjectedSourcesDownwardApi build() {
            return new Jsii$Proxy(this);
        }
    }

    /**
     * An implementation for {@link RevisionSpecVolumesProjectedSourcesDownwardApi}
     */
    @software.amazon.jsii.Internal
    final class Jsii$Proxy extends software.amazon.jsii.JsiiObject implements RevisionSpecVolumesProjectedSourcesDownwardApi {
        private final java.util.List<imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApiItems> items;

        /**
         * Constructor that initializes the object based on values retrieved from the JsiiObject.
         * @param objRef Reference to the JSII managed object.
         */
        protected Jsii$Proxy(final software.amazon.jsii.JsiiObjectRef objRef) {
            super(objRef);
            this.items = software.amazon.jsii.Kernel.get(this, "items", software.amazon.jsii.NativeType.listOf(software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApiItems.class)));
        }

        /**
         * Constructor that initializes the object based on literal property values passed by the {@link Builder}.
         */
        @SuppressWarnings("unchecked")
        protected Jsii$Proxy(final Builder builder) {
            super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
            this.items = (java.util.List<imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApiItems>)builder.items;
        }

        @Override
        public final java.util.List<imports.dev.knative.serving.RevisionSpecVolumesProjectedSourcesDownwardApiItems> getItems() {
            return this.items;
        }

        @Override
        @software.amazon.jsii.Internal
        public com.fasterxml.jackson.databind.JsonNode $jsii$toJson() {
            final com.fasterxml.jackson.databind.ObjectMapper om = software.amazon.jsii.JsiiObjectMapper.INSTANCE;
            final com.fasterxml.jackson.databind.node.ObjectNode data = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();

            if (this.getItems() != null) {
                data.set("items", om.valueToTree(this.getItems()));
            }

            final com.fasterxml.jackson.databind.node.ObjectNode struct = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            struct.set("fqn", om.valueToTree("devknativeserving.RevisionSpecVolumesProjectedSourcesDownwardApi"));
            struct.set("data", data);

            final com.fasterxml.jackson.databind.node.ObjectNode obj = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            obj.set("$jsii.struct", struct);

            return obj;
        }

        @Override
        public final boolean equals(final java.lang.Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RevisionSpecVolumesProjectedSourcesDownwardApi.Jsii$Proxy that = (RevisionSpecVolumesProjectedSourcesDownwardApi.Jsii$Proxy) o;

            return this.items != null ? this.items.equals(that.items) : that.items == null;
        }

        @Override
        public final int hashCode() {
            int result = this.items != null ? this.items.hashCode() : 0;
            return result;
        }
    }
}
