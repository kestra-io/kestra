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
    void shouldAddLightweightTypeStubsWithoutCopyingDefinitions() throws IOException {
        // Given: a bundle covering both task and trigger roots. The Compress subtype carries a
        // type const + title (to exercise metadata extraction); Schedule is bare (fallback to FQCN).
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
                "io.kestra.plugin.compress.archive.Compress": {
                  "properties": {"type": {"const": "io.kestra.plugin.compress.archive.Compress"}},
                  "title": "Compress",
                  "properties2": {"level": {"type": "integer"}}
                },
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

        // Then: the heavy definition is NOT copied — only a lightweight type stub is appended
        Map<String, Object> definitions = (Map<String, Object>) result.get("definitions");
        assertThat(definitions).containsKey("io.kestra.plugin.core.log.Log");
        assertThat(definitions).doesNotContainKey("io.kestra.plugin.compress.archive.Compress");

        Map<String, Object> taskDefinition = (Map<String, Object>) definitions.get("io.kestra.core.models.tasks.Task");
        List<Map<String, Object>> anyOf = (List<Map<String, Object>>) taskDefinition.get("anyOf");
        assertThat(anyOf).hasSize(2);
        // the installed branch stays a $ref, untouched
        assertThat(anyOf.getFirst()).containsEntry("$ref", "#/definitions/io.kestra.plugin.core.log.Log");
        // the catalog subtype is an inline stub: only the type const, plus its title
        Map<String, Object> stub = anyOf.get(1);
        assertThat(stub).doesNotContainKey("$ref");
        assertThat(typeConst(stub)).isEqualTo("io.kestra.plugin.compress.archive.Compress");
        assertThat((List<String>) stub.get("required")).containsExactly("type");
        assertThat(stub).containsEntry("title", "Compress");

        // And: the trigger root, merged from the same bundle, falls back to the FQCN as const
        Map<String, Object> localTriggerSchema = JacksonMapper.ofJson().readValue("""
            {
              "$ref": "#/definitions/io.kestra.core.models.triggers.AbstractTrigger",
              "definitions": {
                "io.kestra.core.models.triggers.AbstractTrigger": {"anyOf": []}
              }
            }
            """, Map.class);
        Map<String, Object> triggerResult = service.mergeWithBundle(SchemaType.TRIGGER, localTriggerSchema);

        Map<String, Object> triggerDefinitions = (Map<String, Object>) triggerResult.get("definitions");
        assertThat(triggerDefinitions).doesNotContainKey("io.kestra.plugin.core.trigger.Schedule");
        Map<String, Object> triggerDefinition = (Map<String, Object>) triggerDefinitions.get("io.kestra.core.models.triggers.AbstractTrigger");
        List<Map<String, Object>> triggerAnyOf = (List<Map<String, Object>>) triggerDefinition.get("anyOf");
        assertThat(triggerAnyOf).hasSize(1);
        assertThat(typeConst(triggerAnyOf.getFirst())).isEqualTo("io.kestra.plugin.core.trigger.Schedule");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExtendNestedTaskDiscriminatorWhenMergingTheFlowSchema() throws IOException {
        // Given: a bundle whose "flow" root isn't itself polymorphic — only the Task definition
        // embedded within it is — proving the merge reaches nested discriminators, not just the
        // schema's own top-level $ref.
        Files.writeString(tempDir.resolve("plugins-schema.json"), """
            {
              "definitions": {
                "io.kestra.core.models.flows.Flow": {"type": "object"},
                "io.kestra.core.models.tasks.Task": {
                  "anyOf": [
                    {"$ref": "#/definitions/io.kestra.plugin.core.log.Log"},
                    {"$ref": "#/definitions/io.kestra.plugin.algolia.Search"}
                  ]
                },
                "io.kestra.plugin.core.log.Log": {"type": "object"},
                "io.kestra.plugin.algolia.Search": {
                  "properties": {"type": {"const": "io.kestra.plugin.algolia.Search"}}
                }
              },
              "roots": {
                "flow": "#/definitions/io.kestra.core.models.flows.Flow",
                "task": "#/definitions/io.kestra.core.models.tasks.Task"
              }
            }
            """);
        PluginSchemaBundleService service = new PluginSchemaBundleService(tempDir.resolve("plugins-schema.json").toUri().toString());

        Map<String, Object> localFlowSchema = JacksonMapper.ofJson().readValue("""
            {
              "$ref": "#/definitions/io.kestra.core.models.flows.Flow",
              "definitions": {
                "io.kestra.core.models.flows.Flow": {"type": "object"},
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
        Map<String, Object> result = service.mergeWithBundle(SchemaType.FLOW, localFlowSchema);

        // Then: the nested Task discriminator (not the Flow root) gets a lightweight stub, and the
        // algolia definition itself is NOT copied into the schema.
        Map<String, Object> definitions = (Map<String, Object>) result.get("definitions");
        assertThat(definitions).doesNotContainKey("io.kestra.plugin.algolia.Search");

        Map<String, Object> taskDefinition = (Map<String, Object>) definitions.get("io.kestra.core.models.tasks.Task");
        List<Map<String, Object>> anyOf = (List<Map<String, Object>>) taskDefinition.get("anyOf");
        assertThat(anyOf).hasSize(2);
        assertThat(anyOf.getFirst()).containsEntry("$ref", "#/definitions/io.kestra.plugin.core.log.Log");
        assertThat(typeConst(anyOf.get(1))).isEqualTo("io.kestra.plugin.algolia.Search");
    }

    @SuppressWarnings("unchecked")
    private static String typeConst(Map<String, Object> branch) {
        Map<String, Object> properties = (Map<String, Object>) branch.get("properties");
        Map<String, Object> type = (Map<String, Object>) properties.get("type");
        return (String) type.get("const");
    }
}
