package io.dws.controller.k8s;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Collects drained previous versions without waiting for the next POST. Disabled by default in
 * tests via {@code dws.reconcile.every}.
 */
@ApplicationScoped
public class ReconcileJob {

    private static final Logger LOG = Logger.getLogger(ReconcileJob.class);

    private final StackApplier applier;

    public ReconcileJob(StackApplier applier) {
        this.applier = applier;
    }

    @Scheduled(every = "{dws.reconcile.every}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void reconcile() {
        try {
            applier.reconcile();
        } catch (RuntimeException e) {
            LOG.warn("Reconcile pass failed; will retry on next tick", e);
        }
    }
}
