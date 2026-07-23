package imports.io.dapr;

/**
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:14:16.463Z")
@software.amazon.jsii.Jsii(module = imports.io.dapr.$Module.class, fqn = "iodapr.Resiliency")
public class Resiliency extends org.cdk8s.ApiObject {

    protected Resiliency(final software.amazon.jsii.JsiiObjectRef objRef) {
        super(objRef);
    }

    protected Resiliency(final software.amazon.jsii.JsiiObject.InitializationMode initializationMode) {
        super(initializationMode);
    }

    static {
        GVK = software.amazon.jsii.JsiiObject.jsiiStaticGet(imports.io.dapr.Resiliency.class, "GVK", software.amazon.jsii.NativeType.forClass(org.cdk8s.GroupVersionKind.class));
    }

    /**
     * Defines a "Resiliency" API object.
     * <p>
     * @param scope the scope in which to define this object. This parameter is required.
     * @param id a scope-local name for the object. This parameter is required.
     * @param props initialization props.
     */
    public Resiliency(final @org.jetbrains.annotations.NotNull software.constructs.Construct scope, final @org.jetbrains.annotations.NotNull java.lang.String id, final @org.jetbrains.annotations.Nullable imports.io.dapr.ResiliencyProps props) {
        super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
        software.amazon.jsii.JsiiEngine.getInstance().createNewObject(this, new Object[] { java.util.Objects.requireNonNull(scope, "scope is required"), java.util.Objects.requireNonNull(id, "id is required"), props });
    }

    /**
     * Defines a "Resiliency" API object.
     * <p>
     * @param scope the scope in which to define this object. This parameter is required.
     * @param id a scope-local name for the object. This parameter is required.
     */
    public Resiliency(final @org.jetbrains.annotations.NotNull software.constructs.Construct scope, final @org.jetbrains.annotations.NotNull java.lang.String id) {
        super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
        software.amazon.jsii.JsiiEngine.getInstance().createNewObject(this, new Object[] { java.util.Objects.requireNonNull(scope, "scope is required"), java.util.Objects.requireNonNull(id, "id is required") });
    }

    /**
     * Renders a Kubernetes manifest for "Resiliency".
     * <p>
     * This can be used to inline resource manifests inside other objects (e.g. as templates).
     * <p>
     * @param props initialization props.
     */
    public static @org.jetbrains.annotations.NotNull java.lang.Object manifest(final @org.jetbrains.annotations.Nullable imports.io.dapr.ResiliencyProps props) {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.io.dapr.Resiliency.class, "manifest", software.amazon.jsii.NativeType.forClass(java.lang.Object.class), new Object[] { props });
    }

    /**
     * Renders a Kubernetes manifest for "Resiliency".
     * <p>
     * This can be used to inline resource manifests inside other objects (e.g. as templates).
     */
    public static @org.jetbrains.annotations.NotNull java.lang.Object manifest() {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.io.dapr.Resiliency.class, "manifest", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
    }

    /**
     * Renders the object to Kubernetes JSON.
     */
    @Override
    public @org.jetbrains.annotations.NotNull java.lang.Object toJson() {
        return software.amazon.jsii.Kernel.call(this, "toJson", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
    }

    /**
     * Returns the apiVersion and kind for "Resiliency".
     */
    public final static org.cdk8s.GroupVersionKind GVK;

    /**
     * A fluent builder for {@link imports.io.dapr.Resiliency}.
     */
    public static final class Builder implements software.amazon.jsii.Builder<imports.io.dapr.Resiliency> {
        /**
         * @return a new instance of {@link Builder}.
         * @param scope the scope in which to define this object. This parameter is required.
         * @param id a scope-local name for the object. This parameter is required.
         */
        public static Builder create(final software.constructs.Construct scope, final java.lang.String id) {
            return new Builder(scope, id);
        }

        private final software.constructs.Construct scope;
        private final java.lang.String id;
        private imports.io.dapr.ResiliencyProps.Builder props;

        private Builder(final software.constructs.Construct scope, final java.lang.String id) {
            this.scope = scope;
            this.id = id;
        }

        /**
         * @return {@code this}
         * @param metadata This parameter is required.
         */
        public Builder metadata(final org.cdk8s.ApiObjectMetadata metadata) {
            this.props().metadata(metadata);
            return this;
        }

        /**
         * @return {@code this}
         * @param scopes This parameter is required.
         */
        public Builder scopes(final java.util.List<java.lang.String> scopes) {
            this.props().scopes(scopes);
            return this;
        }

        /**
         * @return {@code this}
         * @param spec This parameter is required.
         */
        public Builder spec(final imports.io.dapr.ResiliencySpec spec) {
            this.props().spec(spec);
            return this;
        }

        /**
         * @return a newly built instance of {@link imports.io.dapr.Resiliency}.
         */
        @Override
        public imports.io.dapr.Resiliency build() {
            return new imports.io.dapr.Resiliency(
                this.scope,
                this.id,
                this.props != null ? this.props.build() : null
            );
        }

        private imports.io.dapr.ResiliencyProps.Builder props() {
            if (this.props == null) {
                this.props = new imports.io.dapr.ResiliencyProps.Builder();
            }
            return this.props;
        }
    }
}
