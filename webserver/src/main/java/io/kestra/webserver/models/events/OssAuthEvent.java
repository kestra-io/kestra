package io.kestra.webserver.models.events;

import java.time.Instant;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

@Getter
public class OssAuthEvent extends Event {
    private final OssAuth ossAuth;

    @Builder
    public OssAuthEvent(@NotNull OssAuth ossAuth, @NotNull String iid, String uid, @NotNull Instant date) {
        super(
            EventType.OSS_AUTH,
            iid,
            Optional.ofNullable(uid).orElse(iid),
            date,
            0
        );
        this.ossAuth = ossAuth;
    }

    @Getter
    @Builder
    public static class OssAuth {
        private final String email;
    }
}
