package io.kestra.core.models.property;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What the rendering cache of a {@link Property} does, beyond saving a rendering.
 *
 * <p>
 * Each behaviour is written twice: once as it runs by default, and once with {@link Property#skipCache()},
 * which is how a property behaves when its rendering is not reused. The pairs are meant to be read side by
 * side before changing — or removing — the cache:
 * </p>
 *
 * <table border="1">
 * <caption>Rendering cached, or not</caption>
 * <tr><th>Behaviour</th><th>Cached (default)</th><th>Not cached</th></tr>
 * <tr><td>A non-deterministic expression, rendered twice</td><td>one value</td><td>two values</td></tr>
 * <tr><td>Two occurrences of a function within one expression</td><td colspan="2">evaluated separately: the render is cached, not the calls in it</td></tr>
 * <tr><td>Number of evaluations of one expression</td><td>one per context</td><td>one per call</td></tr>
 * <tr><td>Runtime validation of the rendered value</td><td>applied</td><td>silently skipped</td></tr>
 * <tr><td>Two executions rendering the same instance</td><td colspan="2">a value each: the cache is per context</td></tr>
 * </table>
 *
 * <p>
 * Only the last row is purely a cache. The other three are behaviours plugins depend on, which is why the
 * cache cannot be removed without deciding what happens to them first.
 * </p>
 */
@MicronautTest
class PropertyRenderCacheBehaviourTest {

    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void shouldGiveTheSameValueToRepeatedRendersWhenCached() throws Exception {
        Property<String> id = Property.<String> builder().expression("{{ uuid() }}").build();
        var runContext = runContextFactory.of(Map.of());

        String first = Property.as(id, runContext, String.class);
        String second = Property.as(id, runContext, String.class);

        // a task rendering the same property to name a file, then to read it back, gets one name
        assertThat(second).isEqualTo(first);
    }

    @Test
    void shouldGiveADifferentValueToRepeatedRendersWhenNotCached() throws Exception {
        Property<String> id = Property.<String> builder().expression("{{ uuid() }}").build();
        var runContext = runContextFactory.of(Map.of());

        String first = Property.as(id.skipCache(), runContext, String.class);
        String second = Property.as(id.skipCache(), runContext, String.class);

        // the two names differ, and so would a randomPort(), an encrypt() or the result of an http() call
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void shouldEvaluateEachOccurrenceSeparatelyWithinOneRender() throws Exception {
        // what is cached is the result of a render, not the calls inside it: an expression using uuid()
        // twice gives two values in one render, and a second render of the same property repeats them
        Property<String> property = Property.<String> builder().expression("{{ uuid() }} and {{ uuid() }}").build();
        var runContext = runContextFactory.of(Map.of());

        String rendered = Property.as(property, runContext, String.class);
        String[] occurrences = rendered.split(" and ");

        assertThat(occurrences[0]).isNotEqualTo(occurrences[1]);
        assertThat(Property.as(property, runContext, String.class)).isEqualTo(rendered);
    }

    @Test
    void shouldEvaluateTheExpressionOnceWhenCached() throws Exception {
        Property<String> property = Property.<String> builder().expression("{{ value }}").build();
        CountingContext context = new CountingContext(runContextFactory.of(Map.of("value", "rendered")));

        Property.as(property, context, String.class);
        Property.as(property, context, String.class);
        Property.as(property, context, String.class);

        assertThat(context.evaluations).isEqualTo(1);
    }

    @Test
    void shouldEvaluateTheExpressionOnEveryCallWhenNotCached() throws Exception {
        Property<String> property = Property.<String> builder().expression("{{ value }}").build();
        CountingContext context = new CountingContext(runContextFactory.of(Map.of("value", "rendered")));

        Property.as(property.skipCache(), context, String.class);
        Property.as(property.skipCache(), context, String.class);
        Property.as(property.skipCache(), context, String.class);

        assertThat(context.evaluations).isEqualTo(3);
    }

    @Test
    void shouldValidateTheRenderedValueWhenCached() {
        // the value extractor validates the value the rendering left on the property
        var task = taskWithNegativeNumber();
        var runContext = runContextFactory.of(task, Map.of("number", -2));

        var exception = assertThrows(
            ConstraintViolationException.class,
            () -> runContext.render(task.getNumber()).as(Integer.class)
        );
        assertThat(exception.getMessage()).isEqualTo("number: must be greater than or equal to 0");
    }

    @Test
    void shouldNotValidateTheRenderedValueWhenNotCached() throws Exception {
        // skipCache renders into a copy, so the value the extractor reads stays empty and @Min(0) never fires
        var task = taskWithNegativeNumber();
        var runContext = runContextFactory.of(task, Map.of("number", -2));

        assertThat(runContext.render(task.getNumber()).skipCache().as(Integer.class)).contains(-2);
    }

    /** A task whose only constraint violation is its negative {@code number}, so validation is unambiguous. */
    private static DynamicPropertyExampleTask taskWithNegativeNumber() {
        return DynamicPropertyExampleTask.builder()
            .id("dynamic")
            .type(DynamicPropertyExampleTask.class.getName())
            .number(Property.<Integer> builder().expression("{{ number }}").build())
            .string(Property.ofValue("a string"))
            .level(Property.ofValue(Level.INFO))
            .someDuration(Property.ofValue(java.time.Duration.ofSeconds(1)))
            .items(Property.ofValue(java.util.List.of("an item")))
            .properties(Property.ofValue(Map.of("a key", "a value")))
            .from(Map.of("key", "a key", "value", "a value"))
            .build();
    }

    @Test
    void shouldGiveEachExecutionItsOwnValueWhetherCachedOrNot() throws Exception {
        // the one row of the table that is only a cache: sharing one task instance between executions,
        // as the executor does with the flow it keeps in its cache, never shares a rendered value
        Property<String> property = Property.<String> builder().expression("{{ version }}").build();

        assertThat(Property.as(property, runContextFactory.of(Map.of("version", "1.3.9")), String.class)).isEqualTo("1.3.9");
        assertThat(Property.as(property, runContextFactory.of(Map.of("version", "2.0.0")), String.class)).isEqualTo("2.0.0");
    }

    /** Counts how many times an expression is handed to the renderer. */
    private static final class CountingContext implements PropertyContext {
        private final PropertyContext delegate;
        private int evaluations;

        private CountingContext(PropertyContext delegate) {
            this.delegate = delegate;
        }

        @Override
        public String render(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
            this.evaluations++;

            return delegate.render(inline, variables);
        }

        @Override
        public Map<String, Object> render(Map<String, Object> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
            this.evaluations++;

            return delegate.render(inline, variables);
        }
    }
}
