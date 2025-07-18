package io.kestra.core.runners.pebble.functions;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochRandomGenerator;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import java.util.List;
import java.util.Map;

public class UUIDFunction implements Function {

  private static final TimeBasedEpochRandomGenerator generator = Generators.timeBasedEpochRandomGenerator();

  @Override
  public Object execute(
      Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
    return generator.generate().toString();
  }

  @Override
  public List<String> getArgumentNames() {
    return List.of();
  }
}
