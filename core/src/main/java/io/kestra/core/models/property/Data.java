package io.kestra.core.models.property;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * A carrier for structured data items.
 */
public class Data {
    @SuppressWarnings("unchecked")
    private static final Class<Map<String, Object>> MAP_OF_STRING_OBJECT = (Class<Map<String, Object>>) Map.of().getClass();

    // this would be used in case 'from' is a String but not a URI to read it as a single item or a list of items
    private static final ObjectMapper JSON_MAPPER = JacksonMapper.ofJson()
        .copy()
        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    @Nullable
    private final Object from;

    public Data(@Nullable Object from) {
        this.from = from;
    }

    /**
     * Build a carrier Data object for structured data items.
     * The `from` parameter can be either a map, a list of maps, or a String.
     */
    public static Data from(@Nullable Object from) {
        return new Data(from);
    }

    /**
     * Generates a <code>Flux</code> of maps for the data items.
     * If you want to work with objects, use {@link #readAs(RunContext, Class, Function)} instead.
     *
     * @see #readAs(RunContext, Class, Function)
     */
    public Flux<Map<String, Object>> read(RunContext runContext) throws IllegalVariableEvaluationException {
        return readAs(runContext, MAP_OF_STRING_OBJECT, it -> it);
    }

    /**
     * Generates a <code>Flux</code> of objects for the data items.
     * The mapper passed to this method will be used to map to the desired type when the `from` attribute is a Map or a List of Maps.
     * If you want to work with maps, use {@link #read(RunContext)} instead.
     *
     * @see #read(RunContext)
     */
    @SuppressWarnings("unchecked")
    public <T> Flux<T> readAs(RunContext runContext, Class<T> clazz, Function<Map<String, Object>, T> mapper) throws IllegalVariableEvaluationException {
        Objects.requireNonNull(mapper); // as mapper is not used everywhere, we assert it's not null to cover dev issues

        if (from == null) {
            return Flux.empty();
        }

        if (from instanceof Map<?, ?> fromMap) {
            Map<String, Object> map = runContext.render((Map<String, Object>) fromMap);
            return Mono.just(map).flux().map(mapper);
        }

        if (clazz.isAssignableFrom(from.getClass())) {
            // it could be the case in tests so we handle it for dev experience
            return Mono.just((T) from).flux();
        }

        if (from instanceof List<?> fromList) {
            if (!fromList.isEmpty() && clazz.isAssignableFrom(fromList.getFirst().getClass())){
                // it could be the case in tests so we handle it for dev experience
                return Flux.fromIterable((List<T>) fromList);
            }
            Stream<Map<String, Object>> stream = fromList.stream().map(throwFunction(it -> runContext.render((Map<String, Object>) it)));
            return Flux.fromStream(stream).map(mapper);
        }

        if (from instanceof String str) {
            var renderedString = runContext.render(str);
            if (URIFetcher.supports(renderedString)) {
                var uri = URIFetcher.of(runContext.render(str));
                try {
                    var reader = new BufferedReader(new InputStreamReader(uri.fetch(runContext)), FileSerde.BUFFER_SIZE);
                    return FileSerde.readAll(reader, clazz)
                        .publishOn(Schedulers.boundedElastic())
                        .doFinally(signalType -> {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else {
                // Try to parse it as a list of JSON items.
                // A single value instead of a list is also supported as we configure the JSON mapper for it.
                try {
                    CollectionType collectionType = JSON_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz);
                    List<T> list = JSON_MAPPER.readValue(renderedString, collectionType);
                    return Flux.fromIterable(list);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        throw new IllegalArgumentException("Cannot handle structured data of type: " + from.getClass());
    }

    public interface From {
        String TITLE = "Structured data items, either as a map, a list of map, a URI, or a JSON string.";
        String DESCRIPTION = """
                Structured data items can be defined in the following ways:
                - A single item as a map (a document).
                - A list of items as a list of maps (a list of documents).
                - A URI, supported schemes are `kestra` for internal storage files, `file` for host local files, and `nsfile` for namespace files.
                - A JSON String that will then be serialized either as a single item or a list of items.""";

        @Schema(
            title = TITLE,
            description = DESCRIPTION,
            anyOf = {String.class, List.class, Map.class}
        )
        @PluginProperty(dynamic = true)
        Object getFrom();
    }
}
