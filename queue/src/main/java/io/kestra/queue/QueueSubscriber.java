package io.kestra.queue;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.Rethrow;
import lombok.SneakyThrows;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.concurrent.CountDownLatch;
import java.util.function.BiFunction;

public class QueueSubscriber<T extends GenericEvent> extends AbstractQueue<T> {
    private final BiFunction<Rethrow.ConsumerChecked<Either<T, DeserializationException>, Exception>, IsReady, Mono<Void>> function;

    public QueueSubscriber(Class<T> cls, BiFunction<Rethrow.ConsumerChecked<Either<T, DeserializationException>, Exception>, IsReady, Mono<Void>> function) {
        super(cls);

        this.function = function;
    }

    public QueueDisposable subscribe(Rethrow.ConsumerChecked<Either<T, DeserializationException>, Exception> consumer) {
        var isReady = new IsReady();

        Disposable subscribe = function.apply(consumer, isReady)
            .subscribe();

        isReady.await();

        return new QueueDisposable(subscribe);
    }

    public static class IsReady {
        private final CountDownLatch countDownLatch = new CountDownLatch(1);

        public void ready() {
            countDownLatch.countDown();
        }

        @SneakyThrows
        public void await() {
            countDownLatch.await();
        }
    }
}

