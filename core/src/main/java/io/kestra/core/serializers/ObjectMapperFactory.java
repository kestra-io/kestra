package io.kestra.core.serializers;

import io.kestra.core.plugins.Jackson3PluginModule;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Custom Micronaut mapper factory for the HTTP request/response boundary.
 * <p>
 * Micronaut 5 is Jackson 3 based, whereas Kestra's own {@link JacksonMapper} hub stays on Jackson 2, so this
 * mapper is a genuinely separate Jackson 3 mapper (as it already was a separate Jackson 2 one before). It
 * registers {@link Jackson3PluginModule} to restore plugin polymorphic deserialization, and re-asserts the
 * Jackson 2 behaviour for the defaults Jackson 3 flipped.
 * <p>
 * These guardrails are set here rather than in {@code application.yml} on purpose: they are correctness
 * requirements that must hold in every context, including the test contexts whose {@code jackson:} blocks are
 * only partial. Micronaut applies the YAML configuration in {@code jsonMapperBuilder}, which runs before this
 * method, so anything set here deliberately wins over configuration.
 */
@Factory
@BootstrapContextCompatible
@Replaces(factory = io.micronaut.jackson.ObjectMapperFactory.class)
public class ObjectMapperFactory extends io.micronaut.jackson.ObjectMapperFactory {

    @Singleton
    @Secondary
    @Named("json")
    @BootstrapContextCompatible
    @Override
    public JsonMapper jsonMapper(JsonMapper.Builder jsonMapperBuilder) {
        return jsonMapperBuilder
            .addModule(new Jackson3PluginModule())
            // Jackson 3 flips these two relative to Jackson 2. Sorting would reorder every API payload, and
            // failing on trailing tokens would reject bodies Jackson 2 accepted.
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            // Without this, Jackson 3 silently ignores final fields carrying a @Builder.Default initializer
            // (e.g. Property<T> fields on plugin tasks), leaving the default instead of the parsed value.
            .enable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
            // A null for a primitive (e.g. a final boolean field) should fall back to its default, not throw.
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            // The other MapperFeature defaults Jackson 3 changed were checked and need nothing here:
            // DEFAULT_VIEW_INCLUSION (no @JsonView anywhere) and FIX_FIELD_NAME_UPPER_CASE_PREFIX (the only
            // field it could rename, VNodeConsistentHashRing#vNodeCount, is never serialized). Note
            // REQUIRE_TYPE_ID_FOR_SUBTYPES did *not* change - it is already enabled in Jackson 2.22.
            .build();
    }
}
