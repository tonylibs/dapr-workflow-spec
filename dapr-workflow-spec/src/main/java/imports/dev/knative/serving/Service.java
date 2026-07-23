package imports.dev.knative.serving;

/**
 * Service acts as a top-level container that manages a Route and Configuration which implement a network service.
 * <p>
 * Service exists to provide a singular
 * abstraction which can be access controlled, reasoned about, and which
 * encapsulates software lifecycle decisions such as rollout policy and
 * team resource ownership. Service acts only as an orchestrator of the
 * underlying Routes and Configurations (much as a kubernetes Deployment
 * orchestrates ReplicaSets), and its usage is optional but recommended.
 * <p>
 * The Service's controller will track the statuses of its owned Configuration
 * and Route, reflecting their statuses and conditions as its own.
 * <p>
 * See also: https://github.com/knative/serving/blob/main/docs/spec/overview.md#service
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.173Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.Service")
public class Service extends org.cdk8s.ApiObject {

    protected Service(final software.amazon.jsii.JsiiObjectRef objRef) {
        super(objRef);
    }

    protected Service(final software.amazon.jsii.JsiiObject.InitializationMode initializationMode) {
        super(initializationMode);
    }

    static {
        GVK = software.amazon.jsii.JsiiObject.jsiiStaticGet(imports.dev.knative.serving.Service.class, "GVK", software.amazon.jsii.NativeType.forClass(org.cdk8s.GroupVersionKind.class));
    }

    /**
     * Defines a "Service" API object.
     * <p>
     * @param scope the scope in which to define this object. This parameter is required.
     * @param id a scope-local name for the object. This parameter is required.
     * @param props initialization props.
     */
    public Service(final @org.jetbrains.annotations.NotNull software.constructs.Construct scope, final @org.jetbrains.annotations.NotNull java.lang.String id, final @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ServiceProps props) {
        super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
        software.amazon.jsii.JsiiEngine.getInstance().createNewObject(this, new Object[] { java.util.Objects.requireNonNull(scope, "scope is required"), java.util.Objects.requireNonNull(id, "id is required"), props });
    }

    /**
     * Defines a "Service" API object.
     * <p>
     * @param scope the scope in which to define this object. This parameter is required.
     * @param id a scope-local name for the object. This parameter is required.
     */
    public Service(final @org.jetbrains.annotations.NotNull software.constructs.Construct scope, final @org.jetbrains.annotations.NotNull java.lang.String id) {
        super(software.amazon.jsii.JsiiObject.InitializationMode.JSII);
        software.amazon.jsii.JsiiEngine.getInstance().createNewObject(this, new Object[] { java.util.Objects.requireNonNull(scope, "scope is required"), java.util.Objects.requireNonNull(id, "id is required") });
    }

    /**
     * Renders a Kubernetes manifest for "Service".
     * <p>
     * This can be used to inline resource manifests inside other objects (e.g. as templates).
     * <p>
     * @param props initialization props.
     */
    public static @org.jetbrains.annotations.NotNull java.lang.Object manifest(final @org.jetbrains.annotations.Nullable imports.dev.knative.serving.ServiceProps props) {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.dev.knative.serving.Service.class, "manifest", software.amazon.jsii.NativeType.forClass(java.lang.Object.class), new Object[] { props });
    }

    /**
     * Renders a Kubernetes manifest for "Service".
     * <p>
     * This can be used to inline resource manifests inside other objects (e.g. as templates).
     */
    public static @org.jetbrains.annotations.NotNull java.lang.Object manifest() {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.dev.knative.serving.Service.class, "manifest", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
    }

    /**
     * Renders the object to Kubernetes JSON.
     */
    @Override
    public @org.jetbrains.annotations.NotNull java.lang.Object toJson() {
        return software.amazon.jsii.Kernel.call(this, "toJson", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
    }

    /**
     * Returns the apiVersion and kind for "Service".
     */
    public final static org.cdk8s.GroupVersionKind GVK;

    /**
     * A fluent builder for {@link imports.dev.knative.serving.Service}.
     */
    public static final class Builder implements software.amazon.jsii.Builder<imports.dev.knative.serving.Service> {
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
        private imports.dev.knative.serving.ServiceProps.Builder props;

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
         * ServiceSpec represents the configuration for the Service object.
         * <p>
         * A Service's specification is the union of the specifications for a Route
         * and Configuration.  The Service restricts what can be expressed in these
         * fields, e.g. the Route must reference the provided Configuration;
         * however, these limitations also enable friendlier defaulting,
         * e.g. Route never needs a Configuration name, and may be defaulted to
         * the appropriate "run latest" spec.
         * <p>
         * @return {@code this}
         * @param spec ServiceSpec represents the configuration for the Service object. This parameter is required.
         */
        public Builder spec(final imports.dev.knative.serving.ServiceSpec spec) {
            this.props().spec(spec);
            return this;
        }

        /**
         * @return a newly built instance of {@link imports.dev.knative.serving.Service}.
         */
        @Override
        public imports.dev.knative.serving.Service build() {
            return new imports.dev.knative.serving.Service(
                this.scope,
                this.id,
                this.props != null ? this.props.build() : null
            );
        }

        private imports.dev.knative.serving.ServiceProps.Builder props() {
            if (this.props == null) {
                this.props = new imports.dev.knative.serving.ServiceProps.Builder();
            }
            return this.props;
        }
    }
}
