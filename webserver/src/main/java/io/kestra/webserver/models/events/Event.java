package io.kestra.webserver.models.events;

import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.validation.constraints.NotNull;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class Event {
    @NotNull
    protected EventType type;

    @NotNull
    protected String iid;

    @NotNull
    protected Instant date;

    public enum EventType {
        OSS_AUTH
    }
}
