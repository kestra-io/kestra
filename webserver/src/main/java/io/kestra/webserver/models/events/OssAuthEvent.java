package io.kestra.webserver.models.events;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Introspected
public class OssAuthEvent extends Event {
    private final OssAuth ossAuth;

    @Builder
    public OssAuthEvent(@NotNull OssAuth ossAuth, @NotNull String iid, @NotNull Instant date) {
        super(EventType.OSS_AUTH, iid, date);
        this.ossAuth = ossAuth;
    }

    @Getter
    @Builder
    public static class OssAuth {
        private final String email;
    }
}
