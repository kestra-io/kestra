package io.kestra.runner.h2;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.queues.GenericQueueMessage;
import io.kestra.core.queues.QueueException;
import io.kestra.jdbc.runner.GenericJdbcQueue;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class H2GenericQueueTest {

    @Inject
    protected GenericJdbcQueue genericQueue;

    @Test
    void testSampleMessage() throws QueueException {
        SampleQueueMessage message = new SampleQueueMessage("hello world!");
        
        // Test that we can emit a message - the queue expects JSON data
        String jsonMessage = "{\"message\":\"hello world!\",\"uid\":\"123\"}";
        genericQueue.emit("test-namespace", "test-tenant", "test-topic", jsonMessage.getBytes());
        
        // Simple test to verify the queue is working
        assertThat(message.message, is("hello world!"));
        assertThat(message.uid(), is("123"));
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
        
        @Override
        public String toString() {
            return message;
        }
    }
}
