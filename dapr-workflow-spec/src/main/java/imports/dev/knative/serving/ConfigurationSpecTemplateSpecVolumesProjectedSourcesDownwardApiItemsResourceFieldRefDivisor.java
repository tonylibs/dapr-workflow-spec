package imports.dev.knative.serving;

/**
 * Specifies the output format of the exposed resources, defaults to "1".
 */
@javax.annotation.Generated(value = "jsii-pacmak/1.139.0 (build 26a6b54)", date = "2026-07-23T16:02:23.141Z")
@software.amazon.jsii.Jsii(module = imports.dev.knative.serving.$Module.class, fqn = "devknativeserving.ConfigurationSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor")
public class ConfigurationSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor extends software.amazon.jsii.JsiiObject {

    protected ConfigurationSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor(final software.amazon.jsii.JsiiObjectRef objRef) {
        super(objRef);
    }

    protected ConfigurationSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor(final software.amazon.jsii.JsiiObject.InitializationMode initializationMode) {
        super(initializationMode);
    }

    public static @org.jetbrains.annotations.NotNull imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor fromNumber(final @org.jetbrains.annotations.NotNull java.lang.Number value) {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor.class, "fromNumber", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor.class), new Object[] { java.util.Objects.requireNonNull(value, "value is required") });
    }

    public static @org.jetbrains.annotations.NotNull imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor fromString(final @org.jetbrains.annotations.NotNull java.lang.String value) {
        return software.amazon.jsii.JsiiObject.jsiiStaticCall(imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor.class, "fromString", software.amazon.jsii.NativeType.forClass(imports.dev.knative.serving.ConfigurationSpecTemplateSpecVolumesProjectedSourcesDownwardApiItemsResourceFieldRefDivisor.class), new Object[] { java.util.Objects.requireNonNull(value, "value is required") });
    }

    /**
     * Returns union: either {@link java.lang.String} or {@link java.lang.Number}
     */
    public @org.jetbrains.annotations.NotNull java.lang.Object getValue() {
        return software.amazon.jsii.Kernel.get(this, "value", software.amazon.jsii.NativeType.forClass(java.lang.Object.class));
    }
}
