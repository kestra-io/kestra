package io.kestra.core.services;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.async.AsyncEventStreamingService;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Helper that encapsulates the blocking single-action pattern:
 * <ol>
 *   <li>generate a fresh {@code operationId};</li>
 *   <li>register a sink keyed by {@code (operationId, itemId)} on {@link AsyncEventStreamingService};</li>
 *   <li>run the caller's submission callback <em>after</em> registration so the domain event
 *       is emitted once the sink is in place;</li>
 *   <li>block up to {@code timeout} for the processed event;</li>
 *   <li>always unregister the sink via {@code doFinally}.</li>
 * </ol>
 */
@Singleton
public class AsyncOperationWaiter {
    private final AsyncEventStreamingService streamingService;

    @Inject
    public AsyncOperationWaiter(AsyncEventStreamingService streamingService) {
        this.streamingService = streamingService;
    }

    /**
     * Reactive variant: returns a {@link Mono} that completes when the processed event is
     * received, or signals {@link TimeoutException} on timeout. Use this from controllers that
     * return a reactive type to free the request thread during the wait.
     *
     * @param itemId resource id (executionId or trigger uid).
     * @param submit callback that emits the domain event tagged with the generated operationId.
     * @param timeout max wait; on expiry the Mono signals {@link TimeoutException}.
     */
    public Mono<AsyncOperationProcessedEvent> submit(String itemId,
                                                     Consumer<String> submit,
                                                     Duration timeout) {
        String operationId = IdUtils.create();
        return Flux.<AsyncOperationProcessedEvent>create(sink -> {
                streamingService.registerSubscriber(operationId, itemId, sink);
                submit.accept(operationId);
            })
            .timeout(timeout)
            .doFinally(_ -> streamingService.unregisterSubscriber(operationId, itemId))
            .next();
    }

    /**
     * Blocking variant kept for callers that are not yet reactive (e.g. trigger controllers).
     *
     * @param itemId resource id (executionId or trigger uid).
     * @param submit callback that emits the domain event tagged with the generated operationId.
     * @param timeout max wait; on expiry a {@link TimeoutException} is thrown. Reactor wraps
     *        {@code TimeoutException} in a {@code RuntimeException}; this method unwraps it so
     *        callers (controllers) see the checked {@code TimeoutException} directly and can
     *        map it to HTTP 504 with a simple {@code catch (TimeoutException)} clause.
     */
    public AsyncOperationProcessedEvent submitAndWait(String itemId,
                                                      Consumer<String> submit,
                                                      Duration timeout) throws TimeoutException {
        try {
            return submit(itemId, submit, timeout).block();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof TimeoutException timeoutException) {
                throw timeoutException;
            }
            throw e;
        }
    }
}
