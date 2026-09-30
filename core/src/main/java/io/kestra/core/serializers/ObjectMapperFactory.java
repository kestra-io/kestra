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
 * mapper restore plugin polymorphic deserialization, and flips the defaults back to what we used with Jackson 2.
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
            .build();
    }
}
