package io.kestra.core.serializers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.FlowableOnSubscribe;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

abstract public class FileSerde {
    private static final ObjectMapper MAPPER = JacksonMapper.ofIon()
        .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    private static final TypeReference<Object> TYPE_REFERENCE = new TypeReference<>(){};

    public static void write(OutputStream output, Object row) throws IOException {
        output.write(MAPPER.writeValueAsBytes(row));
        output.write("\n".getBytes());
    }

    public static FlowableOnSubscribe<Object> reader(BufferedReader input) {
        return s -> {
            String row;

            while ((row = input.readLine()) != null) {
                s.onNext(convert(row));
            }

            s.onComplete();
        };
    }

    public static void reader(BufferedReader input, Consumer<Object> consumer) throws IOException {
        String row;
        while ((row = input.readLine()) != null) {
            consumer.accept(convert(row));
        }
    }

    private static Object convert(String row) throws JsonProcessingException {
        return MAPPER.readValue(row, TYPE_REFERENCE);
    }
}
