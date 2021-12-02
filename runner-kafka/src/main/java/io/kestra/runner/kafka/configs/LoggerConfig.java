package io.kestra.runner.kafka.configs;

import lombok.Getter;
import org.slf4j.event.Level;

import javax.validation.constraints.NotNull;

@Getter
public class LoggerConfig {
    @NotNull
    private Level level;

    private String topicRegexp;

    private Type type;

    private String keyRegexp;

    private String valueRegexp;

    public enum Type {
        CONSUMER,
        CONSUMER_COMMIT,
        PRODUCER,
        PRODUCER_ACK,
    }
}

