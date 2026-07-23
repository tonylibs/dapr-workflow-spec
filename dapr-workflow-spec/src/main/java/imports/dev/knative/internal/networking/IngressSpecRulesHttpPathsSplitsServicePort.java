package imports.dev.knative.internal.networking;

/**
 * Specifies the port of the referenced service.
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:14.130Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.internal.networking.$Module.class, fqn = "devknativeinternalnetworking.IngressSpecRulesHttpPathsSplitsServicePort")
public class IngressSpecRulesHttpPathsSplitsServicePort extends software.amazon.jsii.JsiiObject {

    protected IngressSpecRulesHttpPathsSplitsServicePort(final software.amazon.jsii.JsiiObjectRef objRef) {
        super(objRef);
    }

    protected IngressSpecRulesHttpPathsSplitsServicePort(final software.amazon.jsii.JsiiObject.InitializationMode initializationMode) {
        super(initializationMode);
    }

    public static @org.jetbrains.annotations.NotNull imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplitsServicePort fromNumber(final @org.jetbrains.annotations.NotNull java.lang.Number value) {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplitsServicePort.class, "fromNumber", software.amazon.jsii.NativeType.forClass(imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplitsServicePort.class), new Object[] { java.util.Objects.requireNonNull(value, "value is required") });
    }

    public static @org.jetbrains.annotations.NotNull imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplitsServicePort fromString(final @org.jetbrains.annotations.NotNull java.lang.String value) {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplitsServicePort.class, "fromString", software.amazon.jsii.NativeType.forClass(imports.dev.knative.internal.networking.IngressSpecRulesHttpPathsSplitsServicePort.class), new Object[] { java.util.Objects.requireNonNull(value, "value is required") });
    }

    /**
     * Returns union: either {@link java.lang.String} or {@link java.lang.Number}
     */
    public @org.jetbrains.annotations.NotNull java.lang.Object getValue() {
        return software.amazon.jsii.Kernel.get(this, "value", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
    }
}
