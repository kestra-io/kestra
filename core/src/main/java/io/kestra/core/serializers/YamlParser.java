package io.kestra.core.serializers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import io.kestra.core.exceptions.InvalidTypeConstraintViolationException;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.models.validations.ValidationError;

import jakarta.annotation.Nullable;
import jakarta.validation.ConstraintViolationException;

public final class YamlParser {
    private static final ObjectMapper NON_STRICT_MAPPER = JacksonMapper.ofYaml()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

    private static final ObjectMapper STRICT_MAPPER = NON_STRICT_MAPPER.copy()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private static final int MAX_PARSE_PROBLEMS = 50;

    public static boolean isValidExtension(Path path) {
        return FilenameUtils.getExtension(path.toFile().getAbsolutePath()).equals("yaml") || FilenameUtils.getExtension(path.toFile().getAbsolutePath()).equals("yml");
    }

    public static <T> T parse(String input, Class<T> cls) {
        return read(input, cls, type(cls));
    }

    public static <T> T parse(String input, Class<T> cls, Boolean strict) {
        return strict ? read(input, cls, type(cls)) : readNonStrict(input, cls, type(cls));
    }

    public static <T> T parse(Map<String, Object> input, Class<T> cls, Boolean strict) {
        ObjectMapper currentMapper = strict ? STRICT_MAPPER : NON_STRICT_MAPPER;

        try {
            return currentMapper.convertValue(input, cls);
        } catch (IllegalArgumentException e) {
            if (e.getCause() instanceof JsonProcessingException jsonProcessingException) {
                throw toConstraintViolationException(input, type(cls), jsonProcessingException);
            }

            throw e;
        }
    }

    /**
     * Scans the source for unknown properties and unknown plugin types, where a strict parse stops at the first one.
     * Each is removed and the parse retried; any other failure ends the scan, and the real parse reports it.
     */
    @SuppressWarnings("unchecked")
    public static ParseReport scan(String input, Class<?> cls) {
        List<ValidationError> unknownProperties = new ArrayList<>();
        List<ParseReport.InvalidType> invalidTypes = new ArrayList<>();
        List<ParseReport.Removal> removals = new ArrayList<>();
        Map<String, Object> map;
        try {
            map = NON_STRICT_MAPPER.readValue(input, Map.class);
        } catch (JsonProcessingException e) {
            return new ParseReport(unknownProperties, invalidTypes, null, removals);
        }

        while (unknownProperties.size() + invalidTypes.size() < MAX_PARSE_PROBLEMS) {
            try {
                STRICT_MAPPER.convertValue(map, cls);
                break;
            } catch (IllegalArgumentException e) {
                if (e.getCause() instanceof UnrecognizedPropertyException unknown) {
                    List<String> at = segments(unknown.getPath());
                    if (removeAt(map, at) == null) {
                        break;
                    }
                    unknownProperties.add(located(unknown.getOriginalMessage(), at, removals));
                } else if (e.getCause() instanceof InvalidTypeIdException invalid) {
                    List<String> at = segments(invalid.getPath());
                    Integer index = removeAt(map, at);
                    if (index == null) {
                        break;
                    }
                    List<String> type = new ArrayList<>(at);
                    type.add("type");
                    invalidTypes.add(new ParseReport.InvalidType(located("Invalid type: " + invalid.getTypeId(), type, removals), invalid.getTypeId()));
                    removals.add(new ParseReport.Removal(index < 0 ? at : at.subList(0, at.size() - 1), index));
                } else {
                    break;
                }
            }
        }
        return new ParseReport(unknownProperties, invalidTypes, map, removals);
    }

    private static ValidationError located(String detail, List<String> segments, List<ParseReport.Removal> removals) {
        List<String> source = ParseReport.shift(segments, removals, removals.size());
        return new ValidationError(detail, ParseReport.toPointer(source), ParseReport.toPath(source));
    }

    private static List<String> segments(List<JsonMappingException.Reference> path) {
        List<String> segments = new ArrayList<>(path.size());
        for (JsonMappingException.Reference reference : path) {
            if (reference.getFieldName() != null) {
                segments.add(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                segments.add(String.valueOf(reference.getIndex()));
            }
        }
        return segments;
    }

    /** Removes the node at {@code path}: returns its list index, -1 for a map key, or null when it is not there. */
    @Nullable
    private static Integer removeAt(Object root, List<String> path) {
        if (path.isEmpty()) {
            return null;
        }
        Object parent = root;
        for (String segment : path.subList(0, path.size() - 1)) {
            parent = child(parent, segment);
            if (parent == null) {
                return null;
            }
        }
        String leaf = path.getLast();
        if (parent instanceof Map<?, ?> map && map.containsKey(leaf)) {
            map.remove(leaf);
            return -1;
        }
        Integer index = parent instanceof List<?> list ? listIndex(list, leaf) : null;
        if (index != null) {
            ((List<?>) parent).remove(index.intValue());
        }
        return index;
    }

    @Nullable
    private static Object child(Object node, String segment) {
        if (node instanceof Map<?, ?> map) {
            return map.get(segment);
        }
        Integer index = node instanceof List<?> list ? listIndex(list, segment) : null;
        return index == null ? null : ((List<?>) node).get(index);
    }

    @Nullable
    private static Integer listIndex(List<?> list, String segment) {
        if (segment.isEmpty() || !segment.chars().allMatch(Character::isDigit) || segment.length() > 9) {
            return null;
        }
        int index = Integer.parseInt(segment);
        return index < list.size() ? index : null;
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

    /** Renders Jackson's reference chain as a document path such as {@code tasks[0].type}. */
    private static String propertyPath(JsonMappingException e, @Nullable String leaf) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : e.getPath()) {
            if (reference.getFieldName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        if (leaf != null) {
            path.append(path.isEmpty() ? "" : ".").append(leaf);
        }
        return path.toString();
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
            return new InvalidTypeConstraintViolationException(
                "Invalid type: " + invalidTypeIdException.getTypeId(),
                invalidTypeIdException.getTypeId(),
                Set.of(
                    ManualConstraintViolation.of(
                        "Invalid type: " + invalidTypeIdException.getTypeId(),
                        target,
                        (Class<T>) target.getClass(),
                        propertyPath(invalidTypeIdException, "type"),
                        null
                    ),
                    ManualConstraintViolation.of(
                        e.getMessage(),
                        target,
                        (Class<T>) target.getClass(),
                        propertyPath(invalidTypeIdException, "type"),
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
                        propertyPath(unrecognizedPropertyException, null),
                        null
                    )
                )
            );
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
