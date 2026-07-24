package imports.dev.knative.internal.networking;

/**
 * Ingress is a collection of rules that allow inbound connections to reach the endpoints defined by a backend.
 * <p>
 * An Ingress can be configured to give services externally-reachable URLs, load
 * balance traffic, offer name based virtual hosting, etc.
 * <p>
 * This is heavily based on K8s Ingress https://godoc.org/k8s.io/api/networking/v1beta1#Ingress
 * which some highlighted modifications.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:14.117Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.networking.$Module.class, fqn = "devknativeinternalnetworking.Ingress")
public class Ingress extends org.cdk8s.ApiObject {

    protected Ingress(final software.amazon.jsii.JsiiObjectRef objRef) {
        super(objRef);
    }

    protected Ingress(final software.amazon.jsii.JsiiObject.InitializationMode initializationMode) {
        super(initializationMode);
    }

    static {
        GVK = software.amazon.jsii.JsiiObject.jsiiStaticGet(imports.dev.knative.internal.networking.Ingress.class, "GVK", software.amazon.jsii.NativeType.forClass(org.cdk8s.GroupVersionKind.class));
    }

    /**
     * Defines a "Ingress" API object.
     * <p>
     * @param scope the scope in which to define this object. This parameter is required.
     * @param id a scope-local name for the object. This parameter is required.
     * @param props initialization props.
     */
    public Ingress(final @org.jetbrains.annotations.NotNull software.constructs.Construct scope, final @org.jetbrains.annotations.NotNull java.lang.String id, final @org.jetbrains.annotations.Nullable imports.dev.knative.internal.networking.IngressProps props) {
        super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
        software.amazon.jsii.JsiiEngine.getInstance().createNewObject(this, new Object[] { java.util.Objects.requireNonNull(scope, "scope is required"), java.util.Objects.requireNonNull(id, "id is required"), props });
    }

    /**
     * Defines a "Ingress" API object.
     * <p>
     * @param scope the scope in which to define this object. This parameter is required.
     * @param id a scope-local name for the object. This parameter is required.
     */
    public Ingress(final @org.jetbrains.annotations.NotNull software.constructs.Construct scope, final @org.jetbrains.annotations.NotNull java.lang.String id) {
        super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
        software.amazon.jsii.JsiiEngine.getInstance().createNewObject(this, new Object[] { java.util.Objects.requireNonNull(scope, "scope is required"), java.util.Objects.requireNonNull(id, "id is required") });
    }

    /**
     * Renders a Kubernetes manifest for "Ingress".
     * <p>
     * This can be used to inline resource manifests inside other objects (e.g. as templates).
     * <p>
     * @param props initialization props.
     */
    public static @org.jetbrains.annotations.NotNull java.lang.Object manifest(final @org.jetbrains.annotations.Nullable imports.dev.knative.internal.networking.IngressProps props) {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.dev.knative.internal.networking.Ingress.class, "manifest", software.amazon.jsii.NativeType.forClass(java.lang.Object.class), new Object[] { props });
    }

    /**
     * Renders a Kubernetes manifest for "Ingress".
     * <p>
     * This can be used to inline resource manifests inside other objects (e.g. as templates).
     */
    public static @org.jetbrains.annotations.NotNull java.lang.Object manifest() {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.dev.knative.internal.networking.Ingress.class, "manifest", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
    }

    /**
     * Renders the object to Kubernetes JSON.
     */
    @Override
    public @org.jetbrains.annotations.NotNull java.lang.Object toJson() {
        return software.amazon.jsii.Kernel.call(this, "toJson", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
    }

    /**
     * Returns the apiVersion and kind for "Ingress".
     */
    public final static org.cdk8s.GroupVersionKind GVK;

    /**
     * A fluent builder for {@link imports.dev.knative.internal.networking.Ingress}.
     */
    public static final class Builder implements software.amazon.jsii.Builder<imports.dev.knative.internal.networking.Ingress> {
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
        private imports.dev.knative.internal.networking.IngressProps.Builder props;

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
         * Spec is the desired state of the Ingress.
         * <p>
         * More info: https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#spec-and-status
         * <p>
         * @return {@code this}
         * @param spec Spec is the desired state of the Ingress. This parameter is required.
         */
        public Builder spec(final imports.dev.knative.internal.networking.IngressSpec spec) {
            this.props().spec(spec);
            return this;
        }

        /**
         * @return a newly built instance of {@link imports.dev.knative.internal.networking.Ingress}.
         */
        @Override
        public imports.dev.knative.internal.networking.Ingress build() {
            return new imports.dev.knative.internal.networking.Ingress(
                this.scope,
                this.id,
                this.props != null ? this.props.build() : null
            );
        }

        private imports.dev.knative.internal.networking.IngressProps.Builder props() {
            if (this.props == null) {
                this.props = new imports.dev.knative.internal.networking.IngressProps.Builder();
            }
            return this.props;
        }
    }
}
