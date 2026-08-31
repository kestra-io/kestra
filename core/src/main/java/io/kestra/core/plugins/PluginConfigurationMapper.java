package io.kestra.core.plugins;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.kestra.core.serializers.DurationDeserializer;
import io.kestra.core.serializers.JacksonMapper;

/**
 * Binds a plugin configuration map onto a plugin class.
 * <p>
 * Configuration files used Micronaut's time literals ({@code 60s}, {@code 5m}, {@code 10ms}) before
 * plugins were bound through Jackson, so they stay accepted here, without changing how durations are
 * parsed anywhere else.
 */
public final class PluginConfigurationMapper {
    private static final SimpleModule DURATION_MODULE = new SimpleModule().addDeserializer(Duration.class, new ConfigurationDurationDeserializer());
    private static final ObjectMapper STRICT_MAPPER = JacksonMapper.ofJson(true).copy().registerModule(DURATION_MODULE);
    private static final ObjectMapper LENIENT_MAPPER = JacksonMapper.ofJson(false).copy().registerModule(DURATION_MODULE);

    private PluginConfigurationMapper() {
    }

    /**
     * @param failOnUnknownProperties whether a key the plugin does not declare is an error; environment variables
     *                                reach the configuration as every split permutation of their name, so a plugin
     *                                type configurable that way has to tolerate unknown keys.
     */
    public static <T> T convert(Map<String, Object> configuration, Class<T> pluginClass, boolean failOnUnknownProperties) {
        return (failOnUnknownProperties ? STRICT_MAPPER : LENIENT_MAPPER).convertValue(configuration, pluginClass);
    }

    static final class ConfigurationDurationDeserializer extends DurationDeserializer {
        // the unit suffixes of Micronaut's TimeConverterRegistrar
        private static final Pattern LITERAL = Pattern.compile("^(-?\\d+)(ns|us|ms|s|m|h|d)$");

        @Override
        protected Duration _fromString(JsonParser parser, DeserializationContext ctxt, String value) throws IOException {
            Matcher matcher = LITERAL.matcher(value.trim());
            if (!matcher.matches()) {
                return super._fromString(parser, ctxt, value);
            }

            long amount = Long.parseLong(matcher.group(1));
            return switch (matcher.group(2)) {
                case "ns" -> Duration.ofNanos(amount);
                case "us" -> Duration.ofNanos(amount * 1000);
                case "ms" -> Duration.ofMillis(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                case "d" -> Duration.ofDays(amount);
                default -> Duration.ofSeconds(amount);
            };
        }
    }
}
