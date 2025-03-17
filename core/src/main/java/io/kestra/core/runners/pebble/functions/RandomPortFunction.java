package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

public class RandomPortFunction implements Function {
  @Override
  public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
      try (ServerSocket tempSocket = new ServerSocket(0)) {
          return tempSocket.getLocalPort();
      } catch (IOException e) {
          throw new PebbleException(
              e,
              "Unable to get random port",
              lineNumber,
              self.getName()
          );
      }
  }

  @Override
  public List<String> getArgumentNames() {
    return List.of();
  }
}
