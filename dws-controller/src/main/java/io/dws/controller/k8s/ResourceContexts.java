package io.dws.controller.k8s;

import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;

/** Dynamic-client contexts for the CRDs the controller manages (no generated Java model needed). */
public final class ResourceContexts {

    public static final ResourceDefinitionContext KNATIVE_SERVICE = new ResourceDefinitionContext.Builder()
            .withGroup("serving.knative.dev")
            .withVersion("v1")
            .withKind("Service")
            .withPlural("services")
            .withNamespaced(true)
            .build();

    public static final ResourceDefinitionContext DAPR_COMPONENT = new ResourceDefinitionContext.Builder()
            .withGroup("dapr.io")
            .withVersion("v1alpha1")
            .withKind("Component")
            .withPlural("components")
            .withNamespaced(true)
            .build();

    private ResourceContexts() {}
}
