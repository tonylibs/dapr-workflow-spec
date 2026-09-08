package io.dws.controller.compile;

import io.serverlessworkflow.api.WorkflowFormat;
import io.serverlessworkflow.api.WorkflowReader;
import io.serverlessworkflow.api.types.Workflow;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

/** Format detection and parse-or-throw, shared by the v1 and v2 compile strategies. */
@UtilityClass
class SpecParser {

  static WorkflowFormat detectFormat(String specText) {
    return specText.stripLeading().startsWith("{") ? WorkflowFormat.JSON : WorkflowFormat.YAML;
  }

  static Workflow parseOrThrow(String specText, WorkflowFormat format) {
    try {
      Workflow workflow = WorkflowReader.readWorkflowFromString(specText, format);
      if (workflow == null) {
        throw new CompilationException(List.of("Definition could not be parsed"));
      }
      return workflow;
    } catch (CompilationException e) {
      throw e;
    } catch (Exception e) {
      throw new CompilationException(collectMessages(e));
    }
  }

  private static List<String> collectMessages(Throwable t) {
    List<String> messages = new ArrayList<>();
    for (Throwable c = t; c != null && c != c.getCause(); c = c.getCause()) {
      if (c.getMessage() != null && !c.getMessage().isBlank()) {
        messages.add(c.getMessage());
      }
    }
    if (messages.isEmpty()) {
      messages.add(t.getClass().getSimpleName());
    }
    return messages;
  }
}
