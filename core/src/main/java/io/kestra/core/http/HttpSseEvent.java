package io.kestra.core.http;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;


@Data
@Builder(toBuilder = true)
public class HttpSseEvent<T> {
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

    /**
     * The data object to write
     */
    T data;

    /**
     * The ID of the event, or null if there is no ID
     */
    String id;

    /**
     * The name of the event
     */
    String name;

    /**
     * A comment for the event, or null if there is no comment
     */
    String comment;

    /**
     * The duration to retry
     */
    Duration retry;

    public <R> HttpSseEvent<R> clone(R data) {
        return HttpSseEvent.<R>builder()
            .data(data)
            .id(this.getId())
            .name(this.getName())
            .comment(this.getComment())
            .retry(this.getRetry())
            .build();
    }
}
