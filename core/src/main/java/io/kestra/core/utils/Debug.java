package io.kestra.core.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public class Debug {
    private static final String NAME = Thread.currentThread().getStackTrace()[2].getClassName();
    private static final Logger LOGGER = LoggerFactory.getLogger(NAME);
    // java.time support is embedded in jackson-databind 3.x, no explicit module registration needed.
    // WRITE_DURATIONS_AS_TIMESTAMPS defaults to false in v3 (was true in v2); keep it enabled to preserve the
    // existing numeric Duration representation used across the codebase (see JacksonMapper.java).
    private static ObjectMapper MAPPER = JsonMapper.builder()
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .configure(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS, true)
        .build();

    private static String caller() {
        return Thread.currentThread().getStackTrace()[3].getClassName() + " -> " +
            Thread.currentThread().getStackTrace()[3].getMethodName() + " # " +
            Thread.currentThread().getStackTrace()[3].getLineNumber();
    }

    public static <T> String toJson(T arg) {
        String output;

        if (arg instanceof String stringValue) {
            output = stringValue;
        } else if (arg instanceof byte[] bytesValue) {
            output = new String(bytesValue);
        } else {
            try {
                output = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(arg);
            } catch (JacksonException e) {
                throw new RuntimeException(e);
            }
        }

        return output;
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> void log(T... args) {
        LOGGER.trace("\033[44;30m " + caller() + " \033[0m");

        for (Object arg : args) {
            LOGGER.trace("\033[46;30m " + arg.getClass().getName() + " \033[0m " + toJson(arg));
        }
    }
}
