package io.kestra.core.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Releases the parts of a {@code multipart/form-data} request that a controller did not use.
 * <p>
 * A bound multipart parameter keeps the bytes already received in direct memory until every part is read, and
 * cancelling the stream does not release them. A controller that answers before reading its parts, because a check
 * failed or the call was malformed, must therefore discard them.
 */
@Slf4j
public final class MultipartUtils {
    private MultipartUtils() {
    }

    /**
     * Read and release every part of a request that will not be used.
     * <p>
     * It is meant to be chained before a rejection, e.g. {@code discard(parts).then(Mono.error(e))}. Failures while
     * reading are ignored, as the caller already answers with the reason the request was rejected.
     *
     * @param parts the parts of the request, {@code null} if it has none
     */
    public static Mono<Void> discard(@Nullable Publisher<? extends CompletedPart> parts) {
        if (parts == null) {
            return Mono.empty();
        }

        return Flux.from(parts)
            .publishOn(Schedulers.boundedElastic())
            .doOnNext(MultipartUtils::release)
            .onErrorResume(e ->
            {
                log.debug("Unable to read the rest of a discarded multipart request.", e);
                return Mono.empty();
            })
            .then();
    }

    /**
     * Run an action that answers a request bound to multipart parts, and release the parts it did not read.
     * <p>
     * Unlike {@link #releasing}, it also covers what the action throws while it builds the answer, the checks that
     * run before the parts are read for instance, which are then reported as an error of the returned {@link Mono}.
     *
     * @param parts  the parts of the request, {@code null} if it has none
     * @param action what answers the request, which is expected to read the parts
     */
    public static <T> Mono<T> guard(@Nullable Publisher<? extends CompletedPart> parts, Action<T> action) {
        Mono<T> answer;
        try {
            answer = action.run();
        } catch (Exception e) {
            return discard(parts).then(Mono.error(e));
        }

        return releasing(parts, answer);
    }

    @FunctionalInterface
    public interface Action<T> {
        Mono<T> run() throws Exception;
    }

    /**
     * Run an action that may answer without reading the parts, and release them once it ends, whatever its outcome.
     * <p>
     * The parts that the action did read are not read twice: a second read of a consumed body is ignored.
     *
     * @param parts  the parts of the request, {@code null} if it has none
     * @param action what answers the request, which is expected to read the parts
     */
    public static <T> Mono<T> releasing(@Nullable Publisher<? extends CompletedPart> parts, Mono<T> action) {
        return action
            .onErrorResume(e -> discard(parts).then(Mono.error(e)))
            .switchIfEmpty(Mono.defer(() -> discard(parts).then(Mono.empty())))
            .flatMap(value -> discard(parts).thenReturn(value));
    }

    /**
     * Read and release a single part that will not be used, such as the file bound to a {@code @Part} parameter.
     * Closing the part is not enough to release its bytes, so it is read. Reading is blocking, so it must not run on an
     * event loop thread.
     *
     * @param part the part, {@code null} if there is none
     */
    public static void release(@Nullable CompletedPart part) {
        if (part == null) {
            return;
        }

        try {
            if (part instanceof CompletedFileUpload fileUpload) {
                try (InputStream content = fileUpload.getInputStream()) {
                    content.transferTo(OutputStream.nullOutputStream());
                }
            } else {
                part.getBytes();
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Unable to read a discarded multipart part.", e);
        }
    }
}
