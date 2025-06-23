package io.kestra.core.models.property;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
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

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class PropertyTest {

    @Inject
    private TestRunContextFactory runContextFactory;

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
            .from("""
                {
                  "key": "{{mapKey}}",
                  "value": "{{mapValue}}"
                }"""
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

        assertThat(output).isNotNull();
        assertThat(output.getValue()).isEqualTo("test - 9 - not-default - PT1M");
        assertThat(output.getLevel()).isEqualTo(Level.INFO);
        assertThat(output.getList()).containsExactlyInAnyOrder("item1", "item2");
        assertThat(output.getMap()).hasSize(2);
        assertThat(output.getMap().get("key1")).isEqualTo("value1");
        assertThat(output.getMap().get("key2")).isEqualTo("value2");
        assertThat(output.getMessages()).hasSize(1);
        assertThat(output.getMessages().getFirst().getKey()).isEqualTo("mapKey");
        assertThat(output.getMessages().getFirst().getValue()).isEqualTo("mapValue");
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
            .from("""
                [
                  {
                     "key": "{{mapKey1}}",
                     "value": "{{mapValue1}}"
                  },
                  {
                     "key": "{{mapKey2}}",
                     "value": "{{mapValue2}}"
                   }
                ]"""
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

        assertThat(output).isNotNull();
        assertThat(output.getValue()).isEqualTo("test - 9 - Default Value - PT1M");
        assertThat(output.getLevel()).isEqualTo(Level.INFO);
        assertThat(output.getList()).containsExactlyInAnyOrder("item1", "item2");
        assertThat(output.getMap()).hasSize(2);
        assertThat(output.getMap().get("key1")).isEqualTo("value1");
        assertThat(output.getMap().get("key2")).isEqualTo("value2");
        assertThat(output.getMessages()).hasSize(2);
        assertThat(output.getMessages().getFirst().getKey()).isEqualTo("mapKey1");
        assertThat(output.getMessages().getFirst().getValue()).isEqualTo("mapValue1");
        assertThat(output.getMessages().get(1).getKey()).isEqualTo("mapKey2");
        assertThat(output.getMessages().get(1).getValue()).isEqualTo("mapValue2");
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
            uri = storage.put(MAIN_TENANT, null, URI.create("/messages.ion"), input);
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
            .from("{{uri}}")
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

        assertThat(output).isNotNull();
        assertThat(output.getValue()).isEqualTo("test - 9 - not-default - PT1M");
        assertThat(output.getLevel()).isEqualTo(Level.INFO);
        assertThat(output.getList()).containsExactlyInAnyOrder("item1", "item2");
        assertThat(output.getMap()).hasSize(2);
        assertThat(output.getMap().get("key1")).isEqualTo("value1");
        assertThat(output.getMap().get("key2")).isEqualTo("value2");
        assertThat(output.getMessages()).hasSize(2);
        assertThat(output.getMessages().getFirst().getKey()).isEqualTo("key1");
        assertThat(output.getMessages().getFirst().getValue()).isEqualTo("value1");
        assertThat(output.getMessages().get(1).getKey()).isEqualTo("key2");
        assertThat(output.getMessages().get(1).getValue()).isEqualTo("value2");
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
            .from(Map.of("key", "{{mapValue}}"))
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
            .from(Map.of("key", "{{mapKey}}", "value", "{{mapValue}}"))
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
        assertThat(exception.getConstraintViolations().size()).isEqualTo(1);
        assertThat(exception.getMessage()).isEqualTo("number: must be greater than or equal to 0");
    }

    @Test
    void ofValue() {
        var prop = Property.ofValue(TestObj.builder().key("key").value("value").build());
        assertThat(prop).isNotNull();
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

        assertThat(output).isNotNull();
        assertThat(output.getList()).containsExactlyInAnyOrder("arrayValue1", "arrayValue2");
        assertThat(output.getMap()).hasSize(2);
        assertThat(output.getMap().get("mapKey1")).isEqualTo("mapValue1");
        assertThat(output.getMap().get("mapKey2")).isEqualTo("mapValue2");
    }

    @Test
    void aListToRender() throws Exception {
        var task = DynamicPropertyExampleTask.builder()
            .items(new Property<>("""
                ["python test.py --input1 \\"{{ item1 }}\\" --input2 \\"{{ item2 }}\\"", "'gs://{{ renderOnce(\\"bucket\\") }}/{{ 'table' }}/{{ 'file' }}_*.csv.gz'"]"""))
            .properties(new Property<>("""
                {
                  "key1": "{{value1}}",
                  "key2": "{{value2}}"
                }"""))
            .build();
        var runContext = runContextFactory.of(Map.ofEntries(
            entry("item1", "item1"),
            entry("item2", "item2"),
            entry("value1", "value1"),
            entry("value2", "value2")
        ));

        var output = task.run(runContext);

        assertThat(output).isNotNull();
        assertThat(output.getList()).containsExactlyInAnyOrder("python test.py --input1 \"item1\" --input2 \"item2\"", "'gs://bucket/table/file_*.csv.gz'");
    }

    @Test
    void fromMessage() throws Exception {
        var task = DynamicPropertyExampleTask.builder()
            .items(new Property<>("""
                ["python test.py --input1 \\"{{ item1 }}\\" --input2 \\"{{ item2 }}\\"", "'gs://{{ renderOnce(\\"bucket\\") }}/{{ 'table' }}/{{ 'file' }}_*.csv.gz'"]"""))
            .properties(new Property<>("""
                {
                  "key1": "{{value1}}",
                  "key2": "{{value2}}"
                }"""))
            .from(DynamicPropertyExampleTask.Message.builder().key("key").value("value").build())
            .build();
        var runContext = runContextFactory.of(Map.ofEntries(
            entry("item1", "item1"),
            entry("item2", "item2"),
            entry("value1", "value1"),
            entry("value2", "value2")
        ));

        var output = task.run(runContext);

        assertThat(output).isNotNull();
        assertThat(output.getMessages()).hasSize(1);
        assertThat(output.getMessages().getFirst().getKey()).isEqualTo("key");
        assertThat(output.getMessages().getFirst().getValue()).isEqualTo("value");
    }

    @Test
    void fromListOfMessages() throws Exception {
        var task = DynamicPropertyExampleTask.builder()
            .items(new Property<>("""
                ["python test.py --input1 \\"{{ item1 }}\\" --input2 \\"{{ item2 }}\\"", "'gs://{{ renderOnce(\\"bucket\\") }}/{{ 'table' }}/{{ 'file' }}_*.csv.gz'"]"""))
            .properties(new Property<>("""
                {
                  "key1": "{{value1}}",
                  "key2": "{{value2}}"
                }"""))
            .from(List.of(
                DynamicPropertyExampleTask.Message.builder().key("key1").value("value1").build(),
                DynamicPropertyExampleTask.Message.builder().key("key2").value("value2").build()
            ))
            .build();
        var runContext = runContextFactory.of(Map.ofEntries(
            entry("item1", "item1"),
            entry("item2", "item2"),
            entry("value1", "value1"),
            entry("value2", "value2")
        ));

        var output = task.run(runContext);

        assertThat(output).isNotNull();
        assertThat(output.getMessages()).hasSize(2);
        assertThat(output.getMessages().getFirst().getKey()).isEqualTo("key1");
        assertThat(output.getMessages().getFirst().getValue()).isEqualTo("value1");
    }

    @Builder
    @Getter
    private static class TestObj {
        private String key;
        private String value;
    }
}