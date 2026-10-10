package io.dws.controller.k8s;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * Collects drained previous versions without waiting for the next POST. Disabled by default in
 * tests via {@code dws.reconcile.every}.
 */
@Slf4j
@ApplicationScoped
public class ReconcileJob {

  private final StackApplier applier;

  public ReconcileJob(StackApplier applier) {
    this.applier = applier;
  }

  @Scheduled(
      every = "{dws.reconcile.every}",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void reconcile() {
    try {
      applier.reconcile();
    } catch (RuntimeException e) {
      log.warn("Reconcile pass failed; will retry on next tick", e);
    }
  }
}
