package io.kestra.webserver.services.ai.agent;

public interface TurnEventSink {
    void emit(String event, Object payload);

    void complete();

    void error(Throwable error);

    default boolean isCancelled() {
        return false;
    }
}
