package io.kestra.core.http;

import lombok.Builder;

import java.time.Duration;

@Builder(toBuilder = true)
public record HttpSseEvent<T> (
    T data,
    String id,
    String name,
    String comment,
    Duration retry
) {
    /**
     * The id parameter.
     */
    public static String ID = "id";

    /**
     * The event parameter.
     */
    public static String EVENT = "event";

    /**
     * The data parameter.
     */
    public static String DATA = "data";

    /**
     * The retry parameter.
     */
    public static String RETRY = "retry";

    public <R> HttpSseEvent<R> clone(R data) {
        return HttpSseEvent.<R>builder()
            .data(data)
            .id(this.id())
            .name(this.name())
            .comment(this.comment())
            .retry(this.retry())
            .build();
    }
}
