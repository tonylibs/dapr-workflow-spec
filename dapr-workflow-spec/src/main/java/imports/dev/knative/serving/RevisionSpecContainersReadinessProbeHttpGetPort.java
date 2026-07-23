package imports.dev.knative.serving;

/**
 * Name or number of the port to access on the container.
 * <p>
 * Number must be in the range 1 to 65535.
 * Name must be an IANA_SVC_NAME.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.160Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.RevisionSpecContainersReadinessProbeHttpGetPort")
public class RevisionSpecContainersReadinessProbeHttpGetPort extends software.amazon.jsii.JsiiObject {

    protected RevisionSpecContainersReadinessProbeHttpGetPort(final software.amazon.jsii.JsiiObjectRef objRef) {
        super(objRef);
    }

    protected RevisionSpecContainersReadinessProbeHttpGetPort(final software.amazon.jsii.JsiiObject.InitializationMode initializationMode) {
        super(initializationMode);
    }

    public static @org.jetbrains.annotations.NotNull imports.dev.knative.serving.RevisionSpecContainersReadinessProbeHttpGetPort fromNumber(final @org.jetbrains.annotations.NotNull java.lang.Number value) {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.dev.knative.serving.RevisionSpecContainersReadinessProbeHttpGetPort.class, "fromNumber", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.RevisionSpecContainersReadinessProbeHttpGetPort.class), new Object[] { java.util.Objects.requireNonNull(value, "value is required") });
    }

    public static @org.jetbrains.annotations.NotNull imports.dev.knative.serving.RevisionSpecContainersReadinessProbeHttpGetPort fromString(final @org.jetbrains.annotations.NotNull java.lang.String value) {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.dev.knative.serving.RevisionSpecContainersReadinessProbeHttpGetPort.class, "fromString", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.RevisionSpecContainersReadinessProbeHttpGetPort.class), new Object[] { java.util.Objects.requireNonNull(value, "value is required") });
    }

    /**
     * Returns union: either {@link java.lang.String} or {@link java.lang.Number}
     */
    public @org.jetbrains.annotations.NotNull java.lang.Object getValue() {
        return software.amazon.jsii.Kernel.get(this, "value", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
    }
}
