package io.kestra.core.models.property;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class PropertyTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void test() throws Exception {
        var task = DynamicPropertyExampleTask.builder()
            .number(new Property<>("{{numberValue}}"))
            .string(new Property<>("{{stringValue}}"))
            .level(new Property<>("{{levelValue}}"))
            .someDuration(new Property<>("{{durationValue}}"))
            .withDefault(new Property<>("{{defaultValue}}"))
            .items(new Property<>("""
                ["{{item1}}", "{{item2}}"]"""))
            .properties(new Property<>("""
                {
                  "key1": "{{value1}}",
                  "key2": "{{value2}}"
                }"""))
            .build();
        var runContext = runContextFactory.of(Map.of(
            "numberValue", 9,
            "stringValue", "test",
            "levelValue", "INFO",
            "durationValue", "PT60S",
            "defaultValue", "not-default",
            "item1", "item1",
            "item2", "item2",
            "value1", "value1",
            "value2", "value2"
        ));

        var output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getValue(), is("test - 9 - not-default - PT1M"));
        assertThat(output.getLevel(), is(Level.INFO));
        assertThat(output.getList(), containsInAnyOrder("item1", "item2"));
        assertThat(output.getMap(), aMapWithSize(2));
        assertThat(output.getMap().get("key1"), is("value1"));
        assertThat(output.getMap().get("key2"), is("value2"));
    }

    @Test
    void withDefaults() throws Exception {
        var task = DynamicPropertyExampleTask.builder()
            .number(new Property<>("{{numberValue}}"))
            .string(new Property<>("{{stringValue}}"))
            .level(new Property<>("{{levelValue}}"))
            .someDuration(new Property<>("{{durationValue}}"))
            .items(new Property<>("""
                ["{{item1}}", "{{item2}}"]"""))
            .properties(new Property<>("""
                {
                  "key1": "{{value1}}",
                  "key2": "{{value2}}"
                }"""))
            .build();
        var runContext = runContextFactory.of(Map.of(
            "numberValue", 9,
            "stringValue", "test",
            "levelValue", "INFO",
            "durationValue", "PT60S",
            "item1", "item1",
            "item2", "item2",
            "value1", "value1",
            "value2", "value2"
        ));

        var output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getValue(), is("test - 9 - Default Value - PT1M"));
        assertThat(output.getLevel(), is(Level.INFO));
        assertThat(output.getList(), containsInAnyOrder("item1", "item2"));
        assertThat(output.getMap(), aMapWithSize(2));
        assertThat(output.getMap().get("key1"), is("value1"));
        assertThat(output.getMap().get("key2"), is("value2"));
    }
}