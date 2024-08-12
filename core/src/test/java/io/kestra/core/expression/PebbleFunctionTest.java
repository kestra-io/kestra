package io.kestra.core.expression;

import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;


class PebbleFunctionTest {

    @Test
    void shouldGetDefaultName() {
        Assertions.assertEquals("testCustom", new TestCustomFunction().name());
        Assertions.assertEquals("testCustom", new TestCustomPebbleFunction().name());
    }

    public static class TestCustomFunction extends PebbleFunction {
        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
            return null;
        }

        @Override
        public List<String> getArgumentNames() {
            return null;
        }
    }

    public static class TestCustomPebbleFunction extends PebbleFunction {
        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
            return null;
        }

        @Override
        public List<String> getArgumentNames() {
            return null;
        }
    }
}