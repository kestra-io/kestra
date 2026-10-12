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
     * Scans the source for every problem a strict parse stops at, removing each and retrying. A problem whose
     * node cannot be removed ends the scan, and the source is only recovered when a lenient parse can read it.
     */
    @SuppressWarnings("unchecked")
    public static ParseReport scan(String input, Class<?> cls, String resource) {
        List<ValidationError> errors = new ArrayList<>();
        List<ParseReport.InvalidType> invalidTypes = new ArrayList<>();
        List<ParseReport.Removal> removals = new ArrayList<>();
        Map<String, Object> map;
        try {
            map = NON_STRICT_MAPPER.readValue(input, Map.class);
        } catch (JsonProcessingException e) {
            errors.addAll(ValidationError.ofException(toConstraintViolationException(input, resource, e)));
            return new ParseReport(errors, invalidTypes, null, removals);
        }

        boolean recoverable = true;
        while (errors.size() + invalidTypes.size() < MAX_PARSE_PROBLEMS) {
            try {
                STRICT_MAPPER.convertValue(map, cls);
                break;
            } catch (IllegalArgumentException e) {
                if (!(e.getCause() instanceof JsonMappingException failure)) {
                    errors.add(ValidationError.of(e.getMessage()));
                    recoverable = false;
                    break;
                }
                List<String> at = segments(failure.getPath());
                if (failure instanceof InvalidTypeIdException invalid) {
                    List<String> type = new ArrayList<>(at);
                    type.add("type");
                    invalidTypes.add(new ParseReport.InvalidType(located("Invalid type: " + invalid.getTypeId(), type, removals), invalid.getTypeId()));
                } else {
                    String detail = failure.getCause() instanceof ConstraintViolationException cve ? cve.getMessage() : failure.getOriginalMessage();
                    errors.add(located(detail, at, removals));
                }
                Integer index = removeAt(map, at);
                if (index == null) {
                    // A lenient parse ignores an unknown property, but stops at any other problem.
                    recoverable = failure instanceof UnrecognizedPropertyException;
                    break;
                }
                if (!(failure instanceof UnrecognizedPropertyException)) {
                    removals.add(new ParseReport.Removal(index < 0 ? at : at.subList(0, at.size() - 1), index));
                }
            }
        }
        return new ParseReport(errors, invalidTypes, recoverable ? toYaml(map) : null, removals);
    }

    @Nullable
    private static String toYaml(Map<String, Object> map) {
        try {
            return NON_STRICT_MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return null;
        }
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
        if (!ParseReport.isIndex(segment)) {
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
        List<String> path = segments(e.getPath());
        if (leaf != null) {
            path.add(leaf);
        }
        return ParseReport.toPath(path);
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
