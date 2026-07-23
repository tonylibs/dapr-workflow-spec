package imports.dev.knative.internal.autoscaling;

/**
 * PodAutoscaler is a Knative abstraction that encapsulates the interface by which Knative components instantiate autoscalers.
 * <p>
 * This definition is an abstraction that may be backed
 * by multiple definitions.  For more information, see the Knative Pluggability presentation:
 * https://docs.google.com/presentation/d/19vW9HFZ6Puxt31biNZF3uLRejDmu82rxJIk1cWmxF7w/edit
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:01:58.166Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.autoscaling.$Module.class, fqn = "devknativeinternalautoscaling.PodAutoscaler")
public class PodAutoscaler extends org.cdk8s.ApiObject {

    protected PodAutoscaler(final software.amazon.jsii.JsiiObjectRef objRef) {
        super(objRef);
    }

    protected PodAutoscaler(final software.amazon.jsii.JsiiObject.InitializationMode initializationMode) {
        super(initializationMode);
    }

    static {
        GVK = software.amazon.jsii.JsiiObject.jsiiStaticGet(imports.dev.knative.internal.autoscaling.PodAutoscaler.class, "GVK", software.amazon.jsii.NativeType.forClass(org.cdk8s.GroupVersionKind.class));
    }

    /**
     * Defines a "PodAutoscaler" API object.
     * <p>
     * @param scope the scope in which to define this object. This parameter is required.
     * @param id a scope-local name for the object. This parameter is required.
     * @param props initialization props.
     */
    public PodAutoscaler(final @org.jetbrains.annotations.NotNull software.constructs.Construct scope, final @org.jetbrains.annotations.NotNull java.lang.String id, final @org.jetbrains.annotations.Nullable imports.dev.knative.internal.autoscaling.PodAutoscalerProps props) {
        super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
        software.amazon.jsii.JsiiEngine.getInstance().createNewObject(this, new Object[] { java.util.Objects.requireNonNull(scope, "scope is required"), java.util.Objects.requireNonNull(id, "id is required"), props });
    }

    /**
     * Defines a "PodAutoscaler" API object.
     * <p>
     * @param scope the scope in which to define this object. This parameter is required.
     * @param id a scope-local name for the object. This parameter is required.
     */
    public PodAutoscaler(final @org.jetbrains.annotations.NotNull software.constructs.Construct scope, final @org.jetbrains.annotations.NotNull java.lang.String id) {
        super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
        software.amazon.jsii.JsiiEngine.getInstance().createNewObject(this, new Object[] { java.util.Objects.requireNonNull(scope, "scope is required"), java.util.Objects.requireNonNull(id, "id is required") });
    }

    /**
     * Renders a Kubernetes manifest for "PodAutoscaler".
     * <p>
     * This can be used to inline resource manifests inside other objects (e.g. as templates).
     * <p>
     * @param props initialization props.
     */
    public static @org.jetbrains.annotations.NotNull java.lang.Object manifest(final @org.jetbrains.annotations.Nullable imports.dev.knative.internal.autoscaling.PodAutoscalerProps props) {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.dev.knative.internal.autoscaling.PodAutoscaler.class, "manifest", software.amazon.jsii.NativeType.forClass(java.lang.Object.class), new Object[] { props });
    }

    /**
     * Renders a Kubernetes manifest for "PodAutoscaler".
     * <p>
     * This can be used to inline resource manifests inside other objects (e.g. as templates).
     */
    public static @org.jetbrains.annotations.NotNull java.lang.Object manifest() {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.dev.knative.internal.autoscaling.PodAutoscaler.class, "manifest", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
    }

    /**
     * Renders the object to Kubernetes JSON.
     */
    @Override
    public @org.jetbrains.annotations.NotNull java.lang.Object toJson() {
        return software.amazon.jsii.Kernel.call(this, "toJson", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
    }

    /**
     * Returns the apiVersion and kind for "PodAutoscaler".
     */
    public final static org.cdk8s.GroupVersionKind GVK;

    /**
     * A fluent builder for {@link imports.dev.knative.internal.autoscaling.PodAutoscaler}.
     */
    public static final class Builder implements software.amazon.jsii.Builder<imports.dev.knative.internal.autoscaling.PodAutoscaler> {
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
        private imports.dev.knative.internal.autoscaling.PodAutoscalerProps.Builder props;

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
         * Spec holds the desired state of the PodAutoscaler (from the client).
         * <p>
         * @return {@code this}
         * @param spec Spec holds the desired state of the PodAutoscaler (from the client). This parameter is required.
         */
        public Builder spec(final imports.dev.knative.internal.autoscaling.PodAutoscalerSpec spec) {
            this.props().spec(spec);
            return this;
        }

        /**
         * @return a newly built instance of {@link imports.dev.knative.internal.autoscaling.PodAutoscaler}.
         */
        @Override
        public imports.dev.knative.internal.autoscaling.PodAutoscaler build() {
            return new imports.dev.knative.internal.autoscaling.PodAutoscaler(
                this.scope,
                this.id,
                this.props != null ? this.props.build() : null
            );
        }

        private imports.dev.knative.internal.autoscaling.PodAutoscalerProps.Builder props() {
            if (this.props == null) {
                this.props = new imports.dev.knative.internal.autoscaling.PodAutoscalerProps.Builder();
            }
            return this.props;
        }
    }
}
