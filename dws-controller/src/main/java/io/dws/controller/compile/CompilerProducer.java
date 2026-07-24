package io.dws.controller.compile;

import io.dws.controller.config.DwsConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/** Wires the framework-free {@link WorkflowCompiler} with configured images and a document fetcher. */
@ApplicationScoped
public class CompilerProducer {

    @Produces
    @ApplicationScoped
    public WorkflowCompiler workflowCompiler(DwsConfig config, OpenApiDocumentFetcher documentFetcher) {
        return new WorkflowCompiler(config.catalog(), documentFetcher);
    }
}
