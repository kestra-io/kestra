package io.kestra.core.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.docs.SchemaType;
import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginSchemaBundleServiceTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        KestraContext kestraContext = mock(KestraContext.class);
        when(kestraContext.getVersion()).thenReturn("1.0.0");
        KestraContext.setContext(kestraContext);
    }

    @AfterEach
    void tearDown() {
        KestraContext.setContext(null);
    }

    @Test
    void shouldReturnLocalSchemaUnchangedWhenDisabled() {
        // Given
        PluginSchemaBundleService service = new PluginSchemaBundleService(null);
        Map<String, Object> localSchema = Map.of("$ref", "#/definitions/io.kestra.core.models.tasks.Task");

        // When
        Map<String, Object> result = service.mergeWithBundle(SchemaType.TASK, localSchema);

        // Then
        assertThat(result).isSameAs(localSchema);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMergeMissingDefinitionAndAnyOfBranchWithoutDuplicatingKnownOnes() throws IOException {
        // Given: a bundle with a shared definitions pool covering both task and trigger roots,
        // proving no per-type duplicate copy is required to serve either one.
        Files.writeString(tempDir.resolve("plugins-schema.json"), """
            {
              "definitions": {
                "io.kestra.core.models.tasks.Task": {
                  "anyOf": [
                    {"$ref": "#/definitions/io.kestra.plugin.core.log.Log"},
                    {"$ref": "#/definitions/io.kestra.plugin.compress.archive.Compress"}
                  ]
                },
                "io.kestra.core.models.triggers.AbstractTrigger": {
                  "anyOf": [
                    {"$ref": "#/definitions/io.kestra.plugin.core.trigger.Schedule"}
                  ]
                },
                "io.kestra.plugin.core.log.Log": {"type": "object"},
                "io.kestra.plugin.compress.archive.Compress": {"type": "object"},
                "io.kestra.plugin.core.trigger.Schedule": {"type": "object"}
              },
              "roots": {
                "task": "#/definitions/io.kestra.core.models.tasks.Task",
                "trigger": "#/definitions/io.kestra.core.models.triggers.AbstractTrigger"
              }
            }
            """);
        PluginSchemaBundleService service = new PluginSchemaBundleService(tempDir.resolve("plugins-schema.json").toUri().toString());

        Map<String, Object> localSchema = JacksonMapper.ofJson().readValue("""
            {
              "$ref": "#/definitions/io.kestra.core.models.tasks.Task",
              "definitions": {
                "io.kestra.core.models.tasks.Task": {
                  "anyOf": [
                    {"$ref": "#/definitions/io.kestra.plugin.core.log.Log"}
                  ]
                },
                "io.kestra.plugin.core.log.Log": {"type": "object"}
              }
            }
            """, Map.class);

        // When
        Map<String, Object> result = service.mergeWithBundle(SchemaType.TASK, localSchema);

        // Then: the missing task gets added to both definitions and anyOf, the known one is not duplicated
        Map<String, Object> definitions = (Map<String, Object>) result.get("definitions");
        assertThat(definitions).containsKeys("io.kestra.plugin.core.log.Log", "io.kestra.plugin.compress.archive.Compress");

        Map<String, Object> taskDefinition = (Map<String, Object>) definitions.get("io.kestra.core.models.tasks.Task");
        List<Map<String, Object>> anyOf = (List<Map<String, Object>>) taskDefinition.get("anyOf");
        assertThat(anyOf).extracting(branch -> branch.get("$ref")).containsExactlyInAnyOrder(
            "#/definitions/io.kestra.plugin.core.log.Log",
            "#/definitions/io.kestra.plugin.compress.archive.Compress"
        );

        // When: the trigger root is merged from the very same bundle file — proving one shared
        // pool serves every schema type instead of each carrying its own duplicate copy
        Map<String, Object> localTriggerSchema = JacksonMapper.ofJson().readValue("""
            {
              "$ref": "#/definitions/io.kestra.core.models.triggers.AbstractTrigger",
              "definitions": {
                "io.kestra.core.models.triggers.AbstractTrigger": {"anyOf": []}
              }
            }
            """, Map.class);
        Map<String, Object> triggerResult = service.mergeWithBundle(SchemaType.TRIGGER, localTriggerSchema);

        // Then
        Map<String, Object> triggerDefinitions = (Map<String, Object>) triggerResult.get("definitions");
        assertThat(triggerDefinitions).containsKey("io.kestra.plugin.core.trigger.Schedule");
        Map<String, Object> triggerDefinition = (Map<String, Object>) triggerDefinitions.get("io.kestra.core.models.triggers.AbstractTrigger");
        assertThat((List<Map<String, Object>>) triggerDefinition.get("anyOf"))
            .extracting(branch -> branch.get("$ref"))
            .containsExactly("#/definitions/io.kestra.plugin.core.trigger.Schedule");
    }
}
