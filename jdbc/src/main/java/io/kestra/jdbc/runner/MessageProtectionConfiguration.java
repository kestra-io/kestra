package io.kestra.jdbc.runner;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

@ConfigurationProperties("kestra.jdbc.queues.message-protection")
@Getter
public class MessageProtectionConfiguration {
    boolean enabled = false;

    Integer limit = 10 * 1024 * 1024;
}
