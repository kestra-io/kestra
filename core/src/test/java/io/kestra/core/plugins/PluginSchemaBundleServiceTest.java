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
    void shouldAddLightweightSubtypeDefinitionsWithoutFullProperties() throws IOException {
        // Given: a bundle covering both task and trigger roots. The Compress subtype carries a
        // type const + title (to exercise metadata extraction) plus a heavy property block that must
        // NOT be copied; Schedule is bare (fallback to FQCN).
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
                  "type": "object",
                  "properties": {
                    "type": {"const": "io.kestra.plugin.compress.archive.Compress"},
                    "level": {"type": "integer", "markdownDescription": "Compression level."},
                    "algorithm": {"$ref": "#/definitions/some.heavy.Nested"}
                  },
                  "required": ["type", "level"],
                  "title": "Compress"
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

        // Then: a lightweight definition is added for the catalog subtype (referenced via $ref, exactly
        // like an installed subtype) — but WITHOUT the plugin's heavy property schema.
        Map<String, Object> definitions = (Map<String, Object>) result.get("definitions");
        assertThat(definitions).containsKeys("io.kestra.plugin.core.log.Log", "io.kestra.plugin.compress.archive.Compress");

        Map<String, Object> taskDefinition = (Map<String, Object>) definitions.get("io.kestra.core.models.tasks.Task");
        List<Map<String, Object>> anyOf = (List<Map<String, Object>>) taskDefinition.get("anyOf");
        assertThat(anyOf).extracting(branch -> branch.get("$ref")).containsExactlyInAnyOrder(
            "#/definitions/io.kestra.plugin.core.log.Log",
            "#/definitions/io.kestra.plugin.compress.archive.Compress"
        );

        Map<String, Object> compress = (Map<String, Object>) definitions.get("io.kestra.plugin.compress.archive.Compress");
        assertThat(compress).containsEntry("type", "object");
        assertThat(defTypeConst(compress)).isEqualTo("io.kestra.plugin.compress.archive.Compress");
        assertThat((List<String>) compress.get("required")).containsExactly("type", "level");
        assertThat(compress).containsEntry("title", "Compress");
        // property NAMES survive (for key completion) with only their doc text — types, nested
        // schemas and $refs (which would dangle) are stripped
        Map<String, Object> compressProperties = (Map<String, Object>) compress.get("properties");
        assertThat(compressProperties).containsOnlyKeys("type", "level", "algorithm");
        assertThat((Map<String, Object>) compressProperties.get("level"))
            .containsOnlyKeys("markdownDescription");
        assertThat((Map<String, Object>) compressProperties.get("algorithm")).isEmpty();

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
        assertThat(triggerDefinitions).containsKey("io.kestra.plugin.core.trigger.Schedule");
        Map<String, Object> triggerDefinition = (Map<String, Object>) triggerDefinitions.get("io.kestra.core.models.triggers.AbstractTrigger");
        List<Map<String, Object>> triggerAnyOf = (List<Map<String, Object>>) triggerDefinition.get("anyOf");
        assertThat(triggerAnyOf).extracting(branch -> branch.get("$ref")).containsExactly("#/definitions/io.kestra.plugin.core.trigger.Schedule");
        assertThat(defTypeConst((Map<String, Object>) triggerDefinitions.get("io.kestra.plugin.core.trigger.Schedule")))
            .isEqualTo("io.kestra.plugin.core.trigger.Schedule");
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

        // Then: the nested Task discriminator (not the Flow root) gets a lightweight definition + $ref.
        Map<String, Object> definitions = (Map<String, Object>) result.get("definitions");
        assertThat(definitions).containsKey("io.kestra.plugin.algolia.Search");
        Map<String, Object> algolia = (Map<String, Object>) definitions.get("io.kestra.plugin.algolia.Search");
        assertThat(algolia).containsEntry("type", "object");
        assertThat(defTypeConst(algolia)).isEqualTo("io.kestra.plugin.algolia.Search");

        Map<String, Object> taskDefinition = (Map<String, Object>) definitions.get("io.kestra.core.models.tasks.Task");
        List<Map<String, Object>> anyOf = (List<Map<String, Object>>) taskDefinition.get("anyOf");
        assertThat(anyOf).extracting(branch -> branch.get("$ref")).containsExactlyInAnyOrder(
            "#/definitions/io.kestra.plugin.core.log.Log",
            "#/definitions/io.kestra.plugin.algolia.Search"
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExtendInlinedSubtypeListsAtPropertySites() throws IOException {
        // Given: the real "flow" schema shape — the generator does NOT route Flow.tasks through the
        // Task discriminator definition; it inlines the full installed-subtype anyOf directly at the
        // property site. The merge must reach those inlined sites, not just the named definition.
        Files.writeString(tempDir.resolve("plugins-schema.json"), """
            {
              "definitions": {
                "io.kestra.core.models.tasks.Task": {
                  "anyOf": [
                    {"$ref": "#/definitions/io.kestra.plugin.core.log.Log"},
                    {"$ref": "#/definitions/io.kestra.plugin.algolia.Search"}
                  ]
                },
                "io.kestra.core.models.triggers.AbstractTrigger": {
                  "anyOf": [
                    {"$ref": "#/definitions/io.kestra.plugin.core.trigger.Schedule"}
                  ]
                },
                "io.kestra.plugin.core.log.Log": {"type": "object"},
                "io.kestra.plugin.algolia.Search": {
                  "properties": {"type": {"const": "io.kestra.plugin.algolia.Search"}}
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

        Map<String, Object> localFlowSchema = JacksonMapper.ofJson().readValue("""
            {
              "$ref": "#/definitions/io.kestra.core.models.flows.Flow",
              "definitions": {
                "io.kestra.core.models.flows.Flow": {
                  "type": "object",
                  "properties": {
                    "tasks": {
                      "type": "array",
                      "items": {
                        "anyOf": [
                          {"$ref": "#/definitions/io.kestra.plugin.core.log.Log"}
                        ]
                      }
                    },
                    "id": {
                      "anyOf": [
                        {"type": "string"},
                        {"type": "null"}
                      ]
                    }
                  }
                },
                "io.kestra.plugin.core.log.Log": {"type": "object"}
              }
            }
            """, Map.class);

        // When
        Map<String, Object> result = service.mergeWithBundle(SchemaType.FLOW, localFlowSchema);

        // Then: the inlined tasks.items.anyOf gains the catalog subtype's $ref …
        Map<String, Object> definitions = (Map<String, Object>) result.get("definitions");
        Map<String, Object> flow = (Map<String, Object>) definitions.get("io.kestra.core.models.flows.Flow");
        Map<String, Object> properties = (Map<String, Object>) flow.get("properties");
        Map<String, Object> items = (Map<String, Object>) ((Map<String, Object>) properties.get("tasks")).get("items");
        List<Map<String, Object>> tasksAnyOf = (List<Map<String, Object>>) items.get("anyOf");
        assertThat(tasksAnyOf).extracting(branch -> branch.get("$ref")).containsExactlyInAnyOrder(
            "#/definitions/io.kestra.plugin.core.log.Log",
            "#/definitions/io.kestra.plugin.algolia.Search"
        );
        assertThat(definitions).containsKey("io.kestra.plugin.algolia.Search");

        // … the trigger discriminator's subtypes are NOT injected there (disjoint sets) …
        assertThat(tasksAnyOf).extracting(branch -> branch.get("$ref"))
            .doesNotContain("#/definitions/io.kestra.plugin.core.trigger.Schedule");

        // … and an anyOf that shares nothing with any discriminator (a plain nullable scalar) is untouched.
        List<Map<String, Object>> idAnyOf = (List<Map<String, Object>>) ((Map<String, Object>) properties.get("id")).get("anyOf");
        assertThat(idAnyOf).hasSize(2);
    }

    @SuppressWarnings("unchecked")
    private static String defTypeConst(Map<String, Object> definition) {
        Map<String, Object> properties = (Map<String, Object>) definition.get("properties");
        Map<String, Object> type = (Map<String, Object>) properties.get("type");
        return (String) type.get("const");
    }
}
