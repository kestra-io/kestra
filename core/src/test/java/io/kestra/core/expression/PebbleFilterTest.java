package io.kestra.core.expression;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;


class PebbleFilterTest {

    @Test
    void shouldGetDefaultName() {
        Assertions.assertEquals("testCustom", new TestCustomFilter().name());
        Assertions.assertEquals("testCustom", new TestCustomPebbleFilter().name());
    }

    public static class TestCustomFilter extends PebbleFilter {

        @Override
        public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
            return null;
        }

        @Override
        public List<String> getArgumentNames() {
            return null;
        }
    }

    public static class TestCustomPebbleFilter extends PebbleFilter {

        @Override
        public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
            return null;
        }

        @Override
        public List<String> getArgumentNames() {
            return null;
        }
    }
}