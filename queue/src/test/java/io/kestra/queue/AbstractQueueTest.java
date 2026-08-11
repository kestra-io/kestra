package io.kestra.queue;

public class AbstractQueueTest {
    protected String keyPrefix() {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[2];
        return e.getMethodName();
    }
}
