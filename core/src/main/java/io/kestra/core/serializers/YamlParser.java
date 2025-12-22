package io.kestra.core.serializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.kestra.core.models.validations.ManualConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class YamlParser {
    private static final ObjectMapper STRICT_MAPPER = JacksonMapper.ofYaml()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

    private static final ObjectMapper NON_STRICT_MAPPER = STRICT_MAPPER.copy()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static boolean isValidExtension(Path path) {
        return FilenameUtils.getExtension(path.toFile().getAbsolutePath()).equals("yaml") || FilenameUtils.getExtension(path.toFile().getAbsolutePath()).equals("yml");
    }

    public static <T> T parse(String input, Class<T> cls) {
        return read(input, cls, type(cls));
    }

    public static <T> T parse(String input, Class<T> cls, Boolean strict) {
        return strict ? read(input, cls, type(cls)) : readNonStrict(input, cls, type(cls));
    }

    public static  <T> T parse(Map<String, Object> input, Class<T> cls, Boolean strict) {
        ObjectMapper currentMapper = strict ? STRICT_MAPPER : NON_STRICT_MAPPER;

        try {
            return currentMapper.convertValue(input, cls);
        } catch (IllegalArgumentException e) {
            if(e.getCause() instanceof JsonProcessingException jsonProcessingException) {
                throw toConstraintViolationException(input, type(cls), jsonProcessingException);
            }

            throw e;
        }
    }

    private static <T> String type(Class<T> cls) {
        return cls.getSimpleName().toLowerCase();
    }

    public static <T> T parse(File file, Class<T> cls) throws ConstraintViolationException {
        try {
            String input = IOUtils.toString(file.toURI(), StandardCharsets.UTF_8);
            return read(input, cls, type(cls));

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

    private static <T> T read(String input, Class<T> objectClass, String resource) {
        try {
            return STRICT_MAPPER.readValue(input, objectClass);
        } catch (JsonProcessingException e) {
            throw toConstraintViolationException(input, resource, e);
        }
    }
    private static <T> T readNonStrict(String input, Class<T> objectClass, String resource) {
        try {
            return NON_STRICT_MAPPER.readValue(input, objectClass);
        } catch (JsonProcessingException e) {
            throw toConstraintViolationException(input, resource, e);
        }
    }
    private static String formatYamlErrorMessage(String originalMessage, JsonProcessingException e) {
        StringBuilder friendlyMessage = new StringBuilder();
        if (originalMessage.contains("Expected a field name")) {
            friendlyMessage.append("YAML syntax error: Invalid structure. Check indentation and ensure all fields are properly formatted.");
        } else if (originalMessage.contains("MappingStartEvent")) {
            friendlyMessage.append("YAML syntax error: Unexpected mapping start. Verify that scalar values are properly quoted if needed.");
        } else if (originalMessage.contains("Scalar value")) {
            friendlyMessage.append("YAML syntax error: Expected a simple value but found complex structure. Check for unquoted special characters.");
        } else {
            friendlyMessage.append("YAML parsing error: ").append(originalMessage.replaceAll("org\\.yaml\\.snakeyaml.*", "").trim());
        }
        if (e.getLocation() != null) {
            int line = e.getLocation().getLineNr();
            friendlyMessage.append(String.format(" (at line %d)", line));
        }
        // Return a generic but cleaner message for other YAML errors
        return friendlyMessage.toString();
    }
    @SuppressWarnings("unchecked")
    public static <T> ConstraintViolationException toConstraintViolationException(T target, String resource, JsonProcessingException e) {
        if (e.getCause() instanceof ConstraintViolationException constraintViolationException) {
            return constraintViolationException;
        } else if (e instanceof InvalidTypeIdException invalidTypeIdException) {
            // This error is thrown when a non-existing task is used
            return new ConstraintViolationException(
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
        } else if (e instanceof UnrecognizedPropertyException unrecognizedPropertyException) {
            var message = unrecognizedPropertyException.getOriginalMessage() + unrecognizedPropertyException.getMessageSuffix();
            return new ConstraintViolationException(
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
        } else {
            String userFriendlyMessage = formatYamlErrorMessage(e.getMessage(), e);
            return new ConstraintViolationException(
                "Illegal " + resource + " source: " + userFriendlyMessage,
                Collections.singleton(
                    ManualConstraintViolation.of(
                        userFriendlyMessage,
                        target,
                        (Class<T>) target.getClass(),
                        "yaml",
                        null
                    )
                )
            );
        }
    }
}
