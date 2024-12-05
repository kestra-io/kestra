package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UUIDFunction implements Function {

  @Override
  public Object execute(
      Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
    return UUID.randomUUID().toString();
  }

  @Override
  public List<String> getArgumentNames() {
    return List.of();
  }
}
