package io.kestra.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Debug {
    private static final String NAME = Thread.currentThread().getStackTrace()[2].getClassName();
    private static final Logger LOGGER = LoggerFactory.getLogger(NAME);
    private static ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static String caller() {
        return Thread.currentThread().getStackTrace()[3].getClassName() + " -> " +
            Thread.currentThread().getStackTrace()[3].getMethodName() + " # " +
            Thread.currentThread().getStackTrace()[3].getLineNumber();
    }

    public static <T> String toJson(T arg) {
        String output;

        if (arg instanceof String) {
            output = (String) arg;
        } else if (arg instanceof byte[]) {
            output = new String((byte[]) arg);
        } else {
            try {
                output = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(arg);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        return output;
    }

    @SafeVarargs
    public static <T> void log(T... args) {
        LOGGER.trace("\033[44;30m " + caller() + " \033[0m");

        for (Object arg : args) {
            LOGGER.trace("\033[46;30m " + arg.getClass().getName() + " \033[0m " + toJson(arg));
        }
    }
}
