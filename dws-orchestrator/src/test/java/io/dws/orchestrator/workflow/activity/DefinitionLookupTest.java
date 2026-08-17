package io.dws.orchestrator.workflow.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dapr.workflows.WorkflowTaskOptions;
import io.dws.orchestrator.expr.JqEvaluator;
import io.dws.orchestrator.workflow.WorkflowSupport;
import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Task;
import io.serverlessworkflow.api.types.Workflow;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link DefinitionLookup#taskByName} recurses into {@code for.do} — the change slice 2.3
 * makes to keep in-process tasks nested under a for loop resolvable by name.
 */
class DefinitionLookupTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private void seed(String yaml) throws Exception {
    Workflow definition = WorkflowReader.readWorkflowFromString(yaml, WorkflowFormat.YAML);
    WorkflowSupport.init(
        definition,
        definition.getDocument().getName(),
        "lookup-workflow",
        "lookup-workflow@v1",
        new JqEvaluator(mapper),
        mapper,
        null,
        mock(WorkflowTaskOptions.class),
        "pubsub");
  }

  @Test
  void taskInsideForDoResolvesByName() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: lookup-workflow
          version: '1.0.0'
        do:
          - loop:
              for:
                each: n
                in: .items
              do:
                - nestedSet:
                    set:
                      done: '"yes"'
        """);
    Task task = DefinitionLookup.taskByName("nestedSet");
    assertThat(task.getSetTask()).isNotNull();
  }

  @Test
  void taskInsideForDoInsideTryResolvesByName() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: lookup-workflow
          version: '1.0.0'
        do:
          - guarded:
              try:
                - loop:
                    for:
                      each: n
                      in: .items
                    do:
                      - deeplyNested:
                          set:
                            done: '"yes"'
              catch:
                do: []
        """);
    Task task = DefinitionLookup.taskByName("deeplyNested");
    assertThat(task.getSetTask()).isNotNull();
  }

  @Test
  void taskInsideForkBranchResolvesByName() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: lookup-workflow
          version: '1.0.0'
        do:
          - raiseAlarm:
              fork:
                compete: false
                branches:
                  - callNurse:
                      set:
                        paged: '"nurse"'
                  - callSecurity:
                      set:
                        paged: '"security"'
        """);
    Task callNurse = DefinitionLookup.taskByName("callNurse");
    assertThat(callNurse.getSetTask()).isNotNull();
    Task callSecurity = DefinitionLookup.taskByName("callSecurity");
    assertThat(callSecurity.getSetTask()).isNotNull();
  }

  @Test
  void taskInsideForkBranchInsideTryResolvesByName() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: lookup-workflow
          version: '1.0.0'
        do:
          - guarded:
              try:
                - raiseAlarm:
                    fork:
                      branches:
                        - deeplyNested:
                            set:
                              done: '"yes"'
              catch:
                do: []
        """);
    Task task = DefinitionLookup.taskByName("deeplyNested");
    assertThat(task.getSetTask()).isNotNull();
  }

  @Test
  void unknownNameStillFailsLoudly() throws Exception {
    seed(
        """
        document:
          dsl: 1.0.0
          namespace: examples
          name: lookup-workflow
          version: '1.0.0'
        do:
          - loop:
              for:
                each: n
                in: .items
              do:
                - present:
                    set:
                      done: '"yes"'
        """);
    assertThatThrownBy(() -> DefinitionLookup.taskByName("absent"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("absent");
  }
}
