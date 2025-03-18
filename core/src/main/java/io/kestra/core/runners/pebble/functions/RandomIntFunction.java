package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.List;
import java.util.Map;

public class RandomIntFunction implements Function {

  @Override
  public Object execute(
      Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
    Long lower = getArgument(args, "lower", self, lineNumber);
    Long upper = getArgument(args, "upper", self, lineNumber);
    if (upper < lower) {
        throw new PebbleException(
            null,
            "In 'randomIn()' upper is less than lower",
            lineNumber,
            self.getName());
    }
    return (int) (Math.floor(Math.random() * (upper - lower)) + lower);
  }

  @Override
  public List<String> getArgumentNames() {
    return List.of("lower", "upper");
  }

  private Long getArgument(
      Map<String, Object> args, String arg, PebbleTemplate self, int lineNumber) {
    if (!args.containsKey(arg)) {
      throw new PebbleException(
          null,
          "The 'randomIn()' function expects an argument " + arg,
          lineNumber,
          self.getName());
    }

    if (!(args.get(arg) instanceof Long)) {
      throw new PebbleException(
          null,
          "The 'randomIn()' function expects an argument " + arg + " of type Long.",
          lineNumber,
          self.getName());
    }
    return (Long) args.get(arg);
  }
}
