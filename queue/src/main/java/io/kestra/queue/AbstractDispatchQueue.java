package io.kestra.queue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.utils.ExecutorsUtils;

import static io.kestra.core.utils.Rethrow.throwFunction;

public abstract class AbstractDispatchQueue<T extends DispatchEvent> extends AbstractQueue<T> implements DispatchQueueInterface<T> {
    public AbstractDispatchQueue(Class<T> cls, QueueService queueService, ExecutorsUtils executorsUtils, MetricRegistry metricRegistry) {
        super(cls, queueService, executorsUtils, metricRegistry);
    }

    @Override
    public final void emit(T message) throws QueueException {
        this.doEmit(this.queueService.serialize(this.cls, message), message.key());

        listeners().forEach(l -> l.accept(message));
        this.emitCounter.increment();
    }

    @Override
    public final void emit(List<T> messages) throws QueueException {
        this.doEmit(
            messages.stream()
                .map(throwFunction(message -> new QueueRecord(message.key(), this.queueService.serialize(this.cls, message))))
                .toList()
        );

        listeners().forEach(l -> messages.forEach(l::accept));
        this.emitCounter.increment(messages.size());
    }

    @Override
    public CompletionStage<Void> emitAsync(T message) {
        return CompletableFuture.runAsync(() ->
        {
            try {
                emit(message);
            } catch (QueueException e) {
                throw new CompletionException(e);
            }
        }, asyncPoolExecutor);
    }

    @Override
    public CompletionStage<Void> emitAsync(List<T> messages) {
        return CompletableFuture.runAsync(() ->
        {
            try {
                emit(messages);
            } catch (QueueException e) {
                throw new CompletionException(e);
            }
        }, asyncPoolExecutor);
    }

    protected abstract void doEmit(byte[] message, String key) throws QueueException;

    protected void doEmit(List<QueueRecord> messages) throws QueueException {
        for (QueueRecord message : messages) {
            doEmit(message.value(), message.key());
        }
    }
}
