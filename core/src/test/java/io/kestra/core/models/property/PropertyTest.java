package io.kestra.core.models.property;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import lombok.Builder;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;
import reactor.core.publisher.Flux;

import java.io.FileInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class PropertyTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storage;

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
            .data(Data.<DynamicPropertyExampleTask.Message>builder()
                .fromMap(new Property<>("""
                    {
                      "key": "{{mapKey}}",
                      "value": "{{mapValue}}"
                    }"""))
                .build()
            )
            .build();
        var runContext = runContextFactory.of(Map.ofEntries(
            entry("numberValue", 9),
            entry("stringValue", "test"),
            entry("levelValue", "INFO"),
            entry("durationValue", "PT60S"),
            entry("defaultValue", "not-default"),
            entry("item1", "item1"),
            entry("item2", "item2"),
            entry("value1", "value1"),
            entry("value2", "value2"),
            entry("mapKey", "mapKey"),
            entry("mapValue", "mapValue")
        ));

        var output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getValue(), is("test - 9 - not-default - PT1M"));
        assertThat(output.getLevel(), is(Level.INFO));
        assertThat(output.getList(), containsInAnyOrder("item1", "item2"));
        assertThat(output.getMap(), aMapWithSize(2));
        assertThat(output.getMap().get("key1"), is("value1"));
        assertThat(output.getMap().get("key2"), is("value2"));
        assertThat(output.getMessages(), hasSize(1));
        assertThat(output.getMessages().getFirst().getKey(), is("mapKey"));
        assertThat(output.getMessages().getFirst().getValue(), is("mapValue"));
    }

    @Test
    void withDefaultsAndMessagesFromList() throws Exception {
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
            .data(Data.<DynamicPropertyExampleTask.Message>builder()
                .fromList(new Property<>("""
                    [
                      {
                         "key": "{{mapKey1}}",
                         "value": "{{mapValue1}}"
                      },
                      {
                         "key": "{{mapKey2}}",
                         "value": "{{mapValue2}}"
                       }
                    ]"""))
                .build()
            )
            .build();
        var runContext = runContextFactory.of(Map.ofEntries(
            entry("numberValue", 9),
            entry("stringValue", "test"),
            entry("levelValue", "INFO"),
            entry("durationValue", "PT60S"),
            entry("item1", "item1"),
            entry("item2", "item2"),
            entry("value1", "value1"),
            entry("value2", "value2"),
            entry("mapKey1", "mapKey1"),
            entry("mapValue1", "mapValue1"),
            entry("mapKey2", "mapKey2"),
            entry("mapValue2", "mapValue2")
        ));

        var output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getValue(), is("test - 9 - Default Value - PT1M"));
        assertThat(output.getLevel(), is(Level.INFO));
        assertThat(output.getList(), containsInAnyOrder("item1", "item2"));
        assertThat(output.getMap(), aMapWithSize(2));
        assertThat(output.getMap().get("key1"), is("value1"));
        assertThat(output.getMap().get("key2"), is("value2"));
        assertThat(output.getMessages(), hasSize(2));
        assertThat(output.getMessages().getFirst().getKey(), is("mapKey1"));
        assertThat(output.getMessages().getFirst().getValue(), is("mapValue1"));
        assertThat(output.getMessages().get(1).getKey(), is("mapKey2"));
        assertThat(output.getMessages().get(1).getValue(), is("mapValue2"));
    }

    @Test
    void withMessagesFromURI() throws Exception {
        Path messages = Files.createTempFile("messages", ".ion");
        final List<DynamicPropertyExampleTask.Message> inputValues = List.of(
            DynamicPropertyExampleTask.Message.builder().key("key1").value("value1").build(),
            DynamicPropertyExampleTask.Message.builder().key("key2").value("value2").build()
        );
        FileSerde.writeAll(Files.newBufferedWriter(messages), Flux.fromIterable(inputValues)).block();
        URI uri;
        try (var input = new FileInputStream(messages.toFile())) {
            uri = storage.put(null, null, URI.create("/messages.ion"), input);
        }

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
            .data(Data.<DynamicPropertyExampleTask.Message>builder().fromURI(new Property<>("{{uri}}")).build())
            .build();
        var runContext = runContextFactory.of(Map.ofEntries(
            entry("numberValue", 9),
            entry("stringValue", "test"),
            entry("levelValue", "INFO"),
            entry("durationValue", "PT60S"),
            entry("defaultValue", "not-default"),
            entry("item1", "item1"),
            entry("item2", "item2"),
            entry("value1", "value1"),
            entry("value2", "value2"),
            entry("uri", uri)
        ));

        var output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getValue(), is("test - 9 - not-default - PT1M"));
        assertThat(output.getLevel(), is(Level.INFO));
        assertThat(output.getList(), containsInAnyOrder("item1", "item2"));
        assertThat(output.getMap(), aMapWithSize(2));
        assertThat(output.getMap().get("key1"), is("value1"));
        assertThat(output.getMap().get("key2"), is("value2"));
        assertThat(output.getMessages(), hasSize(2));
        assertThat(output.getMessages().getFirst().getKey(), is("key1"));
        assertThat(output.getMessages().getFirst().getValue(), is("value1"));
        assertThat(output.getMessages().get(1).getKey(), is("key2"));
        assertThat(output.getMessages().get(1).getValue(), is("value2"));
    }

    @Test
    void failingToRender() throws Exception {
        var task = DynamicPropertyExampleTask.builder()
            .number(new Property<>("{{numberValue}}"))
            .string(new Property<>("{{stringValue}}"))
            .level(new Property<>("{{levelValue}}"))
            .someDuration(new Property<>("{{durationValue}}"))
            .withDefault(new Property<>("{{defaultValue}}"))
            .items(new Property<>("""
                ["{{item1}}", "{{item2}}"]"""))
            .data(Data.<DynamicPropertyExampleTask.Message>builder()
                .fromMap(new Property<>("""
                    {
                      "key": "{{mapValue}}"
                    }"""))
                .build()
            )
            .build();
        var runContext = runContextFactory.of();

        assertThrows(IllegalVariableEvaluationException.class, () -> task.run(runContext));
    }

    @Test
    void shouldFailValidation() throws Exception {
        var task = DynamicPropertyExampleTask.builder()
            .id("dynamic")
            .type(DynamicPropertyExampleTask.class.getName())
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
            .data(Data.<DynamicPropertyExampleTask.Message>builder()
                .fromMap(new Property<>("""
                    {
                      "key": "{{mapKey}}",
                      "value": "{{mapValue}}"
                    }"""))
                .build()
            )
            .build();
        var runContext = runContextFactory.of(task, Map.ofEntries(
            entry("numberValue", -2),
            entry("stringValue", "test"),
            entry("levelValue", "INFO"),
            entry("durationValue", "PT60S"),
            entry("item1", "item1"),
            entry("item2", "item2"),
            entry("value1", "value1"),
            entry("value2", "value2"),
            entry("mapKey", "mapKey"),
            entry("mapValue", "mapValue")
        ));

        var exception = assertThrows(ConstraintViolationException.class, () -> task.run(runContext));
        assertThat(exception.getConstraintViolations().size(), is(1));
        assertThat(exception.getMessage(), is("number: must be greater than or equal to 0"));
    }

    @Test
    void of() {
        var prop = Property.of(TestObj.builder().key("key").value("value").build());
        assertThat(prop, notNullValue());
    }

    @Test
    void arrayAndMapToRender() throws Exception {
        var task = DynamicPropertyExampleTask.builder()
            .items(new Property<>("{{renderOnce(listToRender)}}"))
            .properties(new Property<>("{{renderOnce(mapToRender)}}"))
            .build();
        var runContext = runContextFactory.of(Map.ofEntries(
            entry("arrayValueToRender", "arrayValue1"),
            entry("listToRender", List.of("{{arrayValueToRender}}", "arrayValue2")),
            entry("mapKeyToRender", "mapKey1"),
            entry("mapValueToRender", "mapValue1"),
            entry("mapToRender", Map.of("{{mapKeyToRender}}", "{{mapValueToRender}}", "mapKey2", "mapValue2"))
        ));

        var output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getList(), containsInAnyOrder("arrayValue1", "arrayValue2"));
        assertThat(output.getMap(), aMapWithSize(2));
        assertThat(output.getMap().get("mapKey1"), is("mapValue1"));
        assertThat(output.getMap().get("mapKey2"), is("mapValue2"));
    }

    @Builder
    @Getter
    private static class TestObj {
        private String key;
        private String value;
    }
}