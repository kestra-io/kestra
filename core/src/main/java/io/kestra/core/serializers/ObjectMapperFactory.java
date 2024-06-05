package io.kestra.core.serializers;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.plugins.PluginModule;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.jackson.JacksonConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Custom Micronaut ObjectMapperFactory to add the PluginModule.
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
        ObjectMapper objectMapper = super.objectMapper(jacksonConfiguration, jsonFactory);
        objectMapper.registerModule(new PluginModule());
        return objectMapper;
    }
}
