package io.kestra.core.models.property;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.validations.DataValidation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

@Getter
@Builder
@DataValidation
@Schema(
    title = "A carrier for some data that can comes from either an internal storage URI, an object or an array of objects."
)
public class Data<T> {
    @Schema(title = "A Kestra internal storage URI")
    private Property<URI> fromURI;

    @Schema(title = "An object (which is equivalent to a map)")
    private Property<Map<String, Object>> fromMap;

    @Schema(title = "An array of objects (which is equivalent to a list of maps)")
    private Property<List<Map<String, Object>>> fromList;

    /**
     * Convenient factory method to create a Data object from a URI, mainly for testing purpose.
     *
     * @see #ofMap(Map)
     * @see #ofList(List)
     */
    public static Data<?> ofURI(URI uri) {
        return Data.builder().fromURI(Property.of(uri)).build();
    }

    /**
     * Convenient factory method to create a Data object from a Map, mainly for testing purpose.
     *
     * @see #ofURI(URI)
     * @see #ofList(List)
     */
    public static Data<?> ofMap(Map<String, Object> map) {
        return Data.builder().fromMap(Property.of(map)).build();
    }

    /**
     * Convenient factory method to create a Data object from a List, mainly for testing purpose.
     *
     * @see #ofURI(URI)
     * @see #ofMap(Map)
     */
    public static Data<?> ofList(List<Map<String, Object>> list) {
        return Data.builder().fromList(Property.of(list)).build();
    }

    /**
     * Generates a flux of objects for the data property, using either of its three properties.
     * The mapper passed to this method will be used to map the map to the desired type when using 'fromMap' or 'fromList',
     * it can be omitted when using 'fromURI'.
     */
    public Flux<T> flux(RunContext runContext, Class<T> clazz, Function<Map<String, Object>, T> mapper) throws IllegalVariableEvaluationException {
        if (isFromURI()) {
            URI uri = fromURI.as(runContext, URI.class);
            try {
                var reader = new BufferedReader(new InputStreamReader(runContext.storage().getFile(uri)));
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
        }

        if (isFromMap()) {
            Map<String, Object> map = fromMap.asMap(runContext, String.class, Object.class);
            return Mono.just(map).flux().map(mapper);
        }

        if (isFromList()) {
            List<Map<String, Object>> list = fromList.asList(runContext, Map.class);
            return Flux.fromIterable(list).map(mapper);
        }

        return Flux.empty();
    }

    /**
     * @return true if fromURI is set
     */
    public boolean isFromURI() {
        return fromURI != null;
    }

    /**
     * If a fromURI is present, performs the given action with the URI, otherwise does nothing.
     */
    public void ifFromURI(RunContext runContext, Consumer<URI> consumer) throws IllegalVariableEvaluationException {
        if (isFromURI()) {
            URI uri = fromURI.as(runContext, URI.class);
            consumer.accept(uri);
        }
    }

    /**
     * @return true if fromMap is set
     */
    public boolean isFromMap() {
        return fromMap != null;
    }

    /**
     * If a fromMap is present, performs the given action with the mat, otherwise does nothing.
     */
    public void ifFromMap(RunContext runContext, Consumer<Map<String, Object>> consumer) throws IllegalVariableEvaluationException {
        if (isFromMap()) {
            Map<String, Object> map = fromMap.asMap(runContext, String.class, Object.class);
            consumer.accept(map);
        }
    }

    /**
     * @return true if fromList is set
     */
    public boolean isFromList() {
        return fromList != null;
    }

    /**
     * If a fromList is present, performs the given action with the list of maps, otherwise does nothing.
     */
    public void ifFromList(RunContext runContext, Consumer<List<Map<String, Object>>> consumer) throws IllegalVariableEvaluationException {
        if (isFromList()) {
            List<Map<String, Object>> list = fromList.asList(runContext, Map.class);
            consumer.accept(list);
        }
    }
}
