package io.kestra.core.runners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.encryption.EncryptionService;
import io.kestra.core.exceptions.MissingRequiredArgument;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Data;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.ArrayInput;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.multipart.StreamingFileUpload;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * Service class for manipulating Flow's Inputs and Outputs.
 */
@Singleton
public class FlowInputOutput {
    public static final Pattern URI_PATTERN = Pattern.compile("^[a-z]+:\\/\\/(?:www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_\\+.~#?&\\/=]*)$");

    private final StorageInterface storageInterface;
    private final String secretKey;

    @Inject
    public FlowInputOutput(
        StorageInterface storageInterface,
        @Nullable @Value("${kestra.encryption.secret-key}") String secretKey
    ) {
        this.storageInterface = storageInterface;
        this.secretKey = secretKey;
    }

    /**
     * Utility method for retrieving types inputs for a flow.
     *
     * @param flow      The Flow
     * @param execution The Execution.
     * @param in        The Flow's inputs.
     * @return The Map of typed inputs.
     */
    public Map<String, Object> typedInputs(
        final Flow flow,
        final Execution execution,
        final Map<String, Object> in,
        final Publisher<StreamingFileUpload> files
    ) throws IOException {
        return this.typedInputs(
            flow.getInputs(),
            execution,
            in,
            files
        );
    }

    /**
     * Utility method for retrieving types inputs.
     *
     * @param inputs    The Inputs.
     * @param execution The Execution.
     * @param in        The Flow's inputs.
     * @return The Map of typed inputs.
     */
    public Map<String, Object> typedInputs(
        final List<Input<?>> inputs,
        final Execution execution,
        final Map<String, Object> in,
        final Publisher<StreamingFileUpload> files
    ) throws IOException {
        if (files == null) {
            return this.typedInputs(inputs, execution, in);
        }

        Map<String, String> uploads = Flux.from(files)
            .subscribeOn(Schedulers.boundedElastic())
            .map(throwFunction(file -> {
                File tempFile = File.createTempFile(file.getFilename() + "_", ".upl");
                Publisher<Boolean> uploadPublisher = file.transferTo(tempFile);
                Boolean bool = Mono.from(uploadPublisher).block();

                if (Boolean.FALSE.equals(bool)) {
                    throw new RuntimeException("Can't upload");
                }

                URI from = storageInterface.from(execution, file.getFilename(), tempFile);
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();

                return new AbstractMap.SimpleEntry<>(
                    file.getFilename(),
                    from.toString()
                );
            }))
            .collectMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)
            .block();

        Map<String, Object> merged = new HashMap<>();
        if (in != null) {
            merged.putAll(in);
        }

        merged.putAll(uploads);

        return this.typedInputs(inputs, execution, merged);
    }

    /**
     * Utility method for retrieving types inputs for a flow.
     *
     * @param flow      The inputs Flow?
     * @param execution The Execution.
     * @param in        The Flow's inputs.
     * @return The Map of typed inputs.
     */
    public Map<String, Object> typedInputs(
        final Flow flow,
        final Execution execution,
        final Map<String, Object> in
    ) {
        return this.typedInputs(
            flow.getInputs(),
            execution,
            in
        );
    }

    /**
     * Utility method for retrieving types inputs.
     *
     * @param inputs    The inputs.
     * @param execution The Execution.
     * @param in        The Flow's inputs.
     * @return The Map of typed inputs.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> typedInputs(
        final List<Input<?>> inputs,
        final Execution execution,
        final Map<String, Object> in
    ) {
        if (inputs == null) {
            return ImmutableMap.of();
        }

        Map<String, Object> results = inputs
            .stream()
            .map((Function<Input, Optional<AbstractMap.SimpleEntry<String, Object>>>) input -> {
                Object current = in == null ? null : in.get(input.getId());

                if (current == null && input.getDefaults() != null) {
                    current = input.getDefaults();
                }

                if (input.getRequired() && current == null) {
                    throw new MissingRequiredArgument("Missing required input value '" + input.getId() + "'");
                }

                if (!input.getRequired() && current == null) {
                    return Optional.of(new AbstractMap.SimpleEntry<>(
                        input.getId(),
                        null
                    ));
                }

                var parsedInput = parseData(execution, input, current);
                parsedInput.ifPresent(parsed -> input.validate(parsed.getValue()));
                return parsedInput;
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);

        return handleNestedInputs(results);
    }

    public Map<String, Object> typedOutputs(
        final Flow flow,
        final Execution execution,
        final Map<String, Object> in
    ) {
        if (flow.getOutputs() == null) {
            return ImmutableMap.of();
        }
        Map<String, Object> results = flow
            .getOutputs()
            .stream()
            .map(output -> {
                Object current = in == null ? null : in.get(output.getId());
                return parseData(execution, output, current)
                    .map(entry -> {
                        if (output.getType().equals(Type.SECRET)) {
                            return new AbstractMap.SimpleEntry<>(
                                entry.getKey(),
                                EncryptedString.from(entry.getValue().toString())
                            );
                        }
                        return entry;
                    });
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(HashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);

        // Ensure outputs are compliant with tasks outputs.
        return JacksonMapper.toMap(results);
    }

    private Optional<AbstractMap.SimpleEntry<String, Object>> parseData(
        final Execution execution,
        final Data data,
        final Object current
    ) {
        if (data.getType() == null) {
            return Optional.of(new AbstractMap.SimpleEntry<>(data.getId(), current));
        }

        final Type elementType = data instanceof ArrayInput arrayInput ? arrayInput.getItemType() : null;
        return Optional.of(new AbstractMap.SimpleEntry<>(
            data.getId(),
            parseType(execution, data.getType(), data.getId(), elementType, current)
        ));
    }

    private Object parseType(Execution execution, Type type, String id, Type elementType, Object current) {
        return switch (type) {
            case ENUM, STRING -> current;
            case SECRET -> {
                try {
                    if (secretKey == null) {
                        throw new MissingRequiredArgument("Unable to use a SECRET input/output as encryption is not configured");
                    }
                    yield EncryptionService.encrypt(secretKey, (String) current);
                } catch (GeneralSecurityException e) {
                    throw new MissingRequiredArgument("Invalid SECRET format for '" + id + "' for '" + current + "' with error " + e.getMessage(), e);
                }
            }
            case INT -> current instanceof Integer ? current : Integer.valueOf((String) current);
            case FLOAT -> current instanceof Float ? current : Float.valueOf((String) current);
            case BOOLEAN -> current instanceof Boolean ? current : Boolean.valueOf((String) current);
            case DATETIME -> {
                try {
                    yield Instant.parse(((String) current));
                } catch (DateTimeParseException e) {
                    throw new MissingRequiredArgument("Invalid DATETIME format for '" + id + "' for '" + current + "' with error " + e.getMessage(), e);
                }
            }
            case DATE -> {
                try {
                   yield LocalDate.parse(((String) current));
                } catch (DateTimeParseException e) {
                    throw new MissingRequiredArgument("Invalid DATE format for '" + id + "' for '" + current + "' with error " + e.getMessage(), e);
                }
            }
            case TIME -> {
                try {
                    yield LocalTime.parse(((String) current));
                } catch (DateTimeParseException e) {
                    throw new MissingRequiredArgument("Invalid TIME format for '" + id + "' for '" + current + "' with error " + e.getMessage(), e);
                }
            }
            case DURATION -> {
                try {
                    yield Duration.parse(((String) current));
                } catch (DateTimeParseException e) {
                    throw new MissingRequiredArgument("Invalid DURATION format for '" + id + "' for '" + current + "' with error " + e.getMessage(), e);
                }
            }
            case FILE -> {
                try {
                    URI uri = URI.create(((String) current).replace(File.separator, "/"));

                    if (uri.getScheme() != null && uri.getScheme().equals("kestra")) {
                        yield uri;
                    } else {
                        yield storageInterface.from(execution, id, new File(((String) current)));
                    }
                } catch (Exception e) {
                    throw new MissingRequiredArgument("Invalid FILE format for '" + id + "' for '" + current + "' with error " + e.getMessage(), e);
                }
            }
            case JSON -> {
                try {
                    yield  JacksonMapper.toObject(((String) current));
                } catch (JsonProcessingException e) {
                    throw new MissingRequiredArgument("Invalid JSON format for '" + id + "' for '" + current + "' with error " + e.getMessage(), e);
                }
            }
            case URI -> {
                Matcher matcher = URI_PATTERN.matcher((String) current);
                if (matcher.matches()) {
                    yield current;
                } else {
                    throw new MissingRequiredArgument("Invalid URI format for '" + id + "' for '" + current + "'");
                }
            }
            case ARRAY -> {
                try {
                    if (elementType != null) {
                        // recursively parse the elements only once
                        yield JacksonMapper.toList(((String) current))
                            .stream()
                            .map(element -> parseType(execution, elementType, id, null, element))
                            .toList();
                    } else {
                        yield JacksonMapper.toList(((String) current));
                    }
                } catch (JsonProcessingException e) {
                    throw new MissingRequiredArgument("Invalid JSON format for '" + id + "' for '" + current + "' with error " + e.getMessage(), e);
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleNestedInputs(final Map<String, Object> inputs) {
        Map<String, Object> result = new TreeMap<>();

        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            String[] f = entry.getKey().split("\\.");
            Map<String, Object> t = result;
            int i = 0;
            for (int m = f.length - 1; i < m; ++i) {
                t = (Map<String, Object>) t.computeIfAbsent(f[i], k -> new TreeMap<>());
            }

            t.put(f[i], entry.getValue());
        }

        return result;
    }
}
