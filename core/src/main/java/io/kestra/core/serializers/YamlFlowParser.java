package io.kestra.core.serializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.serializers.helpers.HandleBarDeserializer;
import jakarta.inject.Singleton;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import jakarta.validation.ConstraintViolationException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Singleton
public class YamlFlowParser {
    public static final String CONTEXT_FLOW_DIRECTORY = "flowDirectory";

    private static final ObjectMapper DEFAULT_MAPPER_OPTIONS = JacksonMapper.ofYaml()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    private static final ObjectMapper FLOW_FROM_FILE_MAPPER = DEFAULT_MAPPER_OPTIONS.copy()
        .registerModule(new SimpleModule("HandleBarDeserializer")
            .addDeserializer(String.class, new HandleBarDeserializer(true))
        );

    private static final ObjectMapper FLOW_FROM_STRING_MAPPER = DEFAULT_MAPPER_OPTIONS.copy()
        .registerModule(new SimpleModule("HandleBarDeserializer")
            .addDeserializer(String.class, new HandleBarDeserializer(false))
        );

    public static boolean isValidExtension(Path path) {
        return FilenameUtils.getExtension(path.toFile().getAbsolutePath()).equals("yaml") || FilenameUtils.getExtension(path.toFile().getAbsolutePath()).equals("yml");
    }

    public <T> T parse(String input, Class<T> cls) {
        return readFlow(FLOW_FROM_STRING_MAPPER, input, cls, type(cls));
    }


    public <T> T parse(Map<String, Object> input, Class<T> cls, Boolean strict) {
        ObjectMapper currentMapper = FLOW_FROM_STRING_MAPPER;

        if (!strict) {
            currentMapper = FLOW_FROM_STRING_MAPPER.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }

        try {
            return currentMapper.convertValue(input, cls);
        } catch (IllegalArgumentException e) {
            if(e.getCause() instanceof JsonProcessingException jsonProcessingException) {
                jsonProcessingExceptionHandler(input, type(cls), jsonProcessingException);
            }

            throw e;
        }
    }

    private static <T> String type(Class<T> cls) {
        return cls.getSimpleName().toLowerCase();
    }

    public <T> T parse(File file, Class<T> cls) throws ConstraintViolationException {
        try {
            String input = IOUtils.toString(file.toURI(), StandardCharsets.UTF_8);
            return readFlow(
                FLOW_FROM_FILE_MAPPER.copy()
                    .setInjectableValues(new InjectableValues.Std()
                        .addValue(CONTEXT_FLOW_DIRECTORY, file.getAbsoluteFile().getParentFile().getAbsolutePath())
                    ),
                input,
                cls,
                type(cls)
            );

        } catch (IOException e) {
            throw new ConstraintViolationException(
                "Illegal " + type(cls) + " path:" + e.getMessage(),
                Collections.singleton(
                    ManualConstraintViolation.of(
                        e.getMessage(),
                        file,
                        File.class,
                        type(cls),
                        file.getAbsolutePath()
                    )
                )
            );
        }
    }

    private <T> T readFlow(ObjectMapper mapper, String input, Class<T> objectClass, String resource) {
        try {
            return mapper.readValue(input, objectClass);
        } catch (JsonProcessingException e) {
            jsonProcessingExceptionHandler(input, resource, e);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> void jsonProcessingExceptionHandler(T target, String resource, JsonProcessingException e) throws ConstraintViolationException {
        if (e.getCause() instanceof ConstraintViolationException constraintViolationException) {
            throw constraintViolationException;
        }
        else if (e instanceof InvalidTypeIdException invalidTypeIdException) {
            // This error is thrown when a non-existing task is used
            throw new ConstraintViolationException(
                "Invalid type: " + invalidTypeIdException.getTypeId(),
                Set.of(
                    ManualConstraintViolation.of(
                        "Invalid type: " + invalidTypeIdException.getTypeId(),
                        target,
                        (Class<T>) target.getClass(),
                        invalidTypeIdException.getPathReference(),
                        null
                    ),
                    ManualConstraintViolation.of(
                        e.getMessage(),
                        target,
                        (Class<T>) target.getClass(),
                        invalidTypeIdException.getPathReference(),
                        null
                    )
                )
            );
        }
        else if (e instanceof UnrecognizedPropertyException unrecognizedPropertyException) {
            var message = unrecognizedPropertyException.getOriginalMessage() + unrecognizedPropertyException.getMessageSuffix();
            throw new ConstraintViolationException(
                message,
                Collections.singleton(
                    ManualConstraintViolation.of(
                        e.getCause() == null ? message : message + "\nCaused by: " + e.getCause().getMessage(),
                        target,
                        (Class<T>) target.getClass(),
                        unrecognizedPropertyException.getPathReference(),
                        null
                    )
                ));
        }
        else {
            throw new ConstraintViolationException(
                "Illegal "+ resource +" yaml: " + e.getMessage(),
                Collections.singleton(
                    ManualConstraintViolation.of(
                        e.getCause() == null ? e.getMessage() : e.getMessage() + "\nCaused by: " + e.getCause().getMessage(),
                        target,
                        (Class<T>) target.getClass(),
                        "flow",
                        null
                    )
                )
            );
        }
    }
}

