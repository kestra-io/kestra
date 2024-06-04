package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.encryption.EncryptionService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Data;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.ArrayInput;
import io.kestra.core.models.flows.input.FileInput;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
     * @param flow      The Flow.
     * @param execution The Execution.
     * @param inputs        The Flow's inputs.
     * @return The Map of typed inputs.
     */
    public Map<String, Object> typedInputs(final Flow flow,
                                           final Execution execution,
                                           final Publisher<CompletedPart> inputs) throws IOException {
        return this.typedInputs(flow.getInputs(), execution, inputs);
    }

    /**
     * Utility method for retrieving types inputs for a flow.
     *
     * @param inputs      The inputs
     * @param execution The Execution.
     * @param in        The Execution's inputs.
     * @return The Map of typed inputs.
     */
    public Map<String, Object> typedInputs(final List<Input<?>> inputs,
                                           final Execution execution,
                                           final Publisher<CompletedPart> in) throws IOException {
        Map<String, Object> uploads = Flux.from(in)
            .subscribeOn(Schedulers.boundedElastic())
            .map(throwFunction(input -> {
                if (input instanceof CompletedFileUpload fileUpload) {
                    String fileExtension = inputs.stream().filter(flowInput -> flowInput instanceof FileInput && flowInput.getId().equals(fileUpload.getFilename())).map(flowInput -> ((FileInput) flowInput).getExtension()).findFirst().orElse(".upl");
                    fileExtension = fileExtension.startsWith(".") ? fileExtension : "." + fileExtension;
                    File tempFile = File.createTempFile(fileUpload.getFilename() + "_", fileExtension);
                    try (var inputStream = fileUpload.getInputStream();
                         var outputStream = new FileOutputStream(tempFile)) {
                        long transferredBytes = inputStream.transferTo(outputStream);
                        if (transferredBytes == 0) {
                            throw new RuntimeException("Can't upload file: " + fileUpload.getFilename());
                        }
                    }
                    URI from = storageInterface.from(execution, fileUpload.getFilename(), tempFile);
                    if (!tempFile.delete()) {
                        tempFile.deleteOnExit();
                    }

                    return new AbstractMap.SimpleEntry<>(
                        fileUpload.getFilename(),
                        (Object) from.toString()
                    );

                } else {
                    return new AbstractMap.SimpleEntry<>(
                        input.getName(),
                        (Object) new String(input.getBytes())
                    );
                }
            }))
            .collectMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)
            .block();

        return this.typedInputs(inputs, execution, uploads);
    }

    /**
     * Utility method for retrieving types inputs for a flow.
     *
     * @param flow      The Flow.
     * @param execution The Execution.
     * @param in        The Execution's inputs.
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
     * @param in        The Execution's inputs.
     * @return The Map of typed inputs.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> typedInputs(
        final List<Input<?>> inputs,
        final Execution execution,
        final Map<String, Object> in
    ) throws ConstraintViolationException {
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
                    throw input.toConstraintViolationException(
                        "missing required input",
                        current
                    );
                }

                if (!input.getRequired() && current == null) {
                    return Optional.of(new AbstractMap.SimpleEntry<>(
                        input.getId(),
                        null
                    ));
                }

                try {
                    var parsedInput = parseData(execution, input, current);
                    parsedInput.ifPresent(parsed -> input.validate(parsed.getValue()));
                    return parsedInput;
                } catch (ConstraintViolationException e) {
                    if (e.getConstraintViolations().size() == 1) {
                        throw input.toConstraintViolationException(List.copyOf(e.getConstraintViolations()).getFirst().getMessage(), current);
                    } else {
                        throw input.toConstraintViolationException(e.getMessage(), current);
                    }
                } catch (Exception e) {
                    throw input.toConstraintViolationException(e instanceof IllegalArgumentException ? e.getMessage() : e.toString(), current);
                }
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
                try {
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
                } catch (Exception e) {
                    throw output.toConstraintViolationException(e.getMessage(), current);
                }
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
    ) throws Exception {
        if (data.getType() == null) {
            return Optional.of(new AbstractMap.SimpleEntry<>(data.getId(), current));
        }

        final Type elementType = data instanceof ArrayInput arrayInput ? arrayInput.getItemType() : null;

        return Optional.of(new AbstractMap.SimpleEntry<>(
            data.getId(),
            parseType(execution, data.getType(), data.getId(), elementType, current)
        ));
    }

    private Object parseType(Execution execution, Type type, String id, Type elementType, Object current) throws Exception {
        try {
            return switch (type) {
                case ENUM, STRING -> current;
                case SECRET -> {
                    if (secretKey == null) {
                        throw new Exception("Unable to use a `SECRET` input/output as encryption is not configured");
                    }
                    yield EncryptionService.encrypt(secretKey, (String) current);
                }
                case INT -> current instanceof Integer ? current : Integer.valueOf((String) current);
                case FLOAT -> current instanceof Float ? current : Float.valueOf((String) current);
                case BOOLEAN -> current instanceof Boolean ? current : Boolean.valueOf((String) current);
                case DATETIME -> Instant.parse(((String) current));
                case DATE -> LocalDate.parse(((String) current));
                case TIME -> LocalTime.parse(((String) current));
                case DURATION -> Duration.parse(((String) current));
                case FILE -> {
                    URI uri = URI.create(((String) current).replace(File.separator, "/"));

                    if (uri.getScheme() != null && uri.getScheme().equals("kestra")) {
                        yield uri;
                    } else {
                        yield storageInterface.from(execution, id, new File(((String) current)));
                    }
                }
                case JSON -> JacksonMapper.toObject(((String) current));
                case URI -> {
                    Matcher matcher = URI_PATTERN.matcher((String) current);
                    if (matcher.matches()) {
                        yield current;
                    } else {
                        throw new IllegalArgumentException("Expected `URI` but received `" + current + "`");
                    }
                }
                case ARRAY -> {
                    if (elementType != null) {
                        // recursively parse the elements only once
                        yield JacksonMapper.toList(((String) current))
                            .stream()
                            .map(throwFunction(element -> {
                                try {
                                    return parseType(execution, elementType, id, null, element);
                                } catch (Throwable e) {
                                    throw new IllegalArgumentException("Unable to parse array element as `" + elementType + "` on `" + element + "`", e);
                                }
                            }))
                            .toList();
                    } else {
                        yield JacksonMapper.toList(((String) current));
                    }
                }
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable e) {
            throw new Exception("Expected `" + type + "` but received `" + current + "` with errors:\n```\n" + e.getMessage() + "\n```");
        }
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
