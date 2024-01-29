package io.kestra.core.serializers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.FluxSink;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

abstract public class FileSerde {
    private static final ObjectMapper MAPPER = JacksonMapper.ofIon()
        .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    private static final TypeReference<Object> TYPE_REFERENCE = new TypeReference<>(){};

    public static void write(OutputStream output, Object row) throws IOException {
        if (row != null) { // avoid writing "null"
            output.write(MAPPER.writeValueAsBytes(row));
            output.write("\n".getBytes());
        }
    }

    public static Consumer<FluxSink<Object>> reader(BufferedReader input) {
        return s -> {
            String row;

            try {
                while ((row = input.readLine()) != null) {
                    s.next(convert(row));
                }
                s.complete();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    public static <T> Consumer<FluxSink<T>> reader(BufferedReader input, Class<T> cls) {
        return s -> {
            String row;

            try {
                while ((row = input.readLine()) != null) {
                    s.next(convert(row, cls));
                }
                s.complete();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    public static void reader(BufferedReader input, Consumer<Object> consumer) throws IOException {
        String row;
        while ((row = input.readLine()) != null) {
            consumer.accept(convert(row));
        }
    }

    public static boolean reader(BufferedReader input, int maxLines, Consumer<Object> consumer) throws IOException {
        String row;
        int nbLines = 0;
        while ((row = input.readLine()) != null) {
            if (nbLines >= maxLines) {
                return true;
            }

            consumer.accept(convert(row));
            nbLines ++;
        }

        return false;
    }

    private static Object convert(String row) throws JsonProcessingException {
        return MAPPER.readValue(row, TYPE_REFERENCE);
    }

    private static <T> T convert(String row, Class<T> cls) throws JsonProcessingException {
        return MAPPER.readValue(row, cls);
    }
}
