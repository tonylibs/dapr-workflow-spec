package imports.dev.knative.internal.networking;

/**
 * ServerlessService is a proxy for the K8s service objects containing the endpoints for the revision, whether those are endpoints of the activator or revision pods.
 * <p>
 * See: https://knative.page.link/naxz for details.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:14.131Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.networking.$Module.class, fqn = "devknativeinternalnetworking.ServerlessService")
public class ServerlessService extends org.cdk8s.ApiObject {

    protected ServerlessService(final software.amazon.jsii.JsiiObjectRef objRef) {
        super(objRef);
    }

    protected ServerlessService(final software.amazon.jsii.JsiiObject.InitializationMode initializationMode) {
        super(initializationMode);
    }

    static {
        GVK = software.amazon.jsii.JsiiObject.jsiiStaticGet(imports.dev.knative.internal.networking.ServerlessService.class, "GVK", software.amazon.jsii.NativeType.forClass(org.cdk8s.GroupVersionKind.class));
    }

    /**
     * Defines a "ServerlessService" API object.
     * <p>
     * @param scope the scope in which to define this object. This parameter is required.
     * @param id a scope-local name for the object. This parameter is required.
     * @param props initialization props.
     */
    public ServerlessService(final @org.jetbrains.annotations.NotNull software.constructs.Construct scope, final @org.jetbrains.annotations.NotNull java.lang.String id, final @org.jetbrains.annotations.Nullable imports.dev.knative.internal.networking.ServerlessServiceProps props) {
        super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
        software.amazon.jsii.JsiiEngine.getInstance().createNewObject(this, new Object[] { java.util.Objects.requireNonNull(scope, "scope is required"), java.util.Objects.requireNonNull(id, "id is required"), props });
    }

    /**
     * Defines a "ServerlessService" API object.
     * <p>
     * @param scope the scope in which to define this object. This parameter is required.
     * @param id a scope-local name for the object. This parameter is required.
     */
    public ServerlessService(final @org.jetbrains.annotations.NotNull software.constructs.Construct scope, final @org.jetbrains.annotations.NotNull java.lang.String id) {
        super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
        software.amazon.jsii.JsiiEngine.getInstance().createNewObject(this, new Object[] { java.util.Objects.requireNonNull(scope, "scope is required"), java.util.Objects.requireNonNull(id, "id is required") });
    }

    /**
     * Renders a Kubernetes manifest for "ServerlessService".
     * <p>
     * This can be used to inline resource manifests inside other objects (e.g. as templates).
     * <p>
     * @param props initialization props.
     */
    public static @org.jetbrains.annotations.NotNull java.lang.Object manifest(final @org.jetbrains.annotations.Nullable imports.dev.knative.internal.networking.ServerlessServiceProps props) {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.dev.knative.internal.networking.ServerlessService.class, "manifest", software.amazon.jsii.NativeType.forClass(java.lang.Object.class), new Object[] { props });
    }

    /**
     * Renders a Kubernetes manifest for "ServerlessService".
     * <p>
     * This can be used to inline resource manifests inside other objects (e.g. as templates).
     */
    public static @org.jetbrains.annotations.NotNull java.lang.Object manifest() {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.dev.knative.internal.networking.ServerlessService.class, "manifest", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
    }

    /**
     * Renders the object to Kubernetes JSON.
     */
    @Override
    public @org.jetbrains.annotations.NotNull java.lang.Object toJson() {
        return software.amazon.jsii.Kernel.call(this, "toJson", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
    }

    /**
     * Returns the apiVersion and kind for "ServerlessService".
     */
    public final static org.cdk8s.GroupVersionKind GVK;

    /**
     * A fluent builder for {@link imports.dev.knative.internal.networking.ServerlessService}.
     */
    public static final class Builder implements software.amazon.jsii.Builder<imports.dev.knative.internal.networking.ServerlessService> {
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
        private imports.dev.knative.internal.networking.ServerlessServiceProps.Builder props;

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
         * Spec is the desired state of the ServerlessService.
         * <p>
         * More info: https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#spec-and-status
         * <p>
         * @return {@code this}
         * @param spec Spec is the desired state of the ServerlessService. This parameter is required.
         */
        public Builder spec(final imports.dev.knative.internal.networking.ServerlessServiceSpec spec) {
            this.props().spec(spec);
            return this;
        }

        /**
         * @return a newly built instance of {@link imports.dev.knative.internal.networking.ServerlessService}.
         */
        @Override
        public imports.dev.knative.internal.networking.ServerlessService build() {
            return new imports.dev.knative.internal.networking.ServerlessService(
                this.scope,
                this.id,
                this.props != null ? this.props.build() : null
            );
        }

        private imports.dev.knative.internal.networking.ServerlessServiceProps.Builder props() {
            if (this.props == null) {
                this.props = new imports.dev.knative.internal.networking.ServerlessServiceProps.Builder();
            }
            return this.props;
        }
    }
}
