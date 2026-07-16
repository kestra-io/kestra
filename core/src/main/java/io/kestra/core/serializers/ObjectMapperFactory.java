package io.kestra.core.serializers;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.jackson.JacksonConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Custom Micronaut ObjectMapperFactory.
 * <p>
 * PluginModule is now Jackson 3-based (see {@link io.kestra.core.plugins.PluginModule}) and can no longer be
 * registered on this Jackson 2 Micronaut-managed mapper. Plugin polymorphic deserialization through Micronaut's
 * HTTP layer is unavailable until the Micronaut 5 bump (which brings Jackson 3 support) lands on this branch.
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
    public ObjectMapper objectMapper(@Nullable JacksonConfiguration jacksonConfiguration, @Nullable JsonFactory jsonFactory) {
        return super.objectMapper(jacksonConfiguration, jsonFactory);
    }
}
