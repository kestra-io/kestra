package io.kestra.jdbc.runner;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.queues.GenericQueueMessage;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.concurrent.CountDownLatch;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class GenericJdbcQueueTest {

    @Inject
    @Named(QueueFactoryInterface.GENERICQUEUE_NAMED)
    protected QueueInterface<GenericQueueMessage> genericQueue;

    @Test
    void testSampleMessage() throws QueueException {
        SampleQueueMessage message = new SampleQueueMessage("hello world!");
        genericQueue.emit(message);
        CountDownLatch countdownLatch = new CountDownLatch(1);
        Flux<GenericQueueMessage> receivedMessage = TestsUtils.receive(genericQueue, (messageWrapper) -> {
            countdownLatch.countDown();
        });
        var value = (SampleQueueMessage) receivedMessage.blockLast();
        assertThat(value.message, is(message.message));
    }

    public static class SampleQueueMessage extends GenericQueueMessage {
        private String message;

        public SampleQueueMessage(String message) {
            this.message = message;
        }

        @Override
        public String uid() {
            return "123";
        }
    }
}
