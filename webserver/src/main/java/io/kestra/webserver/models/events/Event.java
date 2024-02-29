package io.kestra.webserver.models.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class Event {
    @NotNull
    protected EventType type;

    @NotNull
    protected String iid;

    @NotNull
    protected String uid;

    @NotNull
    protected Instant date;

    @NotNull
    @JsonInclude
    protected Integer counter;

    public enum EventType {
        OSS_AUTH
    }
}
