package io.kestra.runner.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ListUtils;
import io.kestra.runner.kafka.configs.ClientConfig;
import io.kestra.runner.kafka.configs.LoggerConfig;
import io.kestra.runner.kafka.services.KafkaStreamService;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Slf4j
public abstract class AbstractInterceptor {
    private final static StringDeserializer STRING_DESERIALIZER = new StringDeserializer();

    ClientConfig clientConfig;

    protected <K, V> void logRecord(
        LoggerConfig.Type type,
        String topic,
        Integer partition,
        @Nullable Long offset,
        Long timestamp,
        K key,
        V value
    ) {
        Level level = isMatch(type, topic, key, value);

        if (level == null) {
            return;
        }

        String format = "[{} > {}{}{}{}] {} = {}";
        Object[] args = {
            type,
            topic,
            partition != null ? "[" + partition + "]" : "",
            offset != null ? "@" + offset : "",
            timestamp != null ? " " + Instant.ofEpochMilli(timestamp) : "",
            deserialize(key),
            deserialize(value)
        };

        this.log(level, format, args);
    }

    protected void log(Level level, String format, Object[] args) {
        if (level == Level.TRACE) {
            log.trace(format, args);
        } else if (level == Level.DEBUG) {
            log.debug(format, args);
        } else if (level == Level.INFO) {
            log.info(format, args);
        } else if (level == Level.WARN) {
            log.warn(format, args);
        } else if (level == Level.ERROR) {
            log.error(format, args);
        }
    }

    protected <K, V> Level isMatch(LoggerConfig.Type type, String topic) {
        return ListUtils.emptyOnNull(this.clientConfig.getLoggers())
            .stream()
            .map(loggerConfig -> {
                if (logEnabled(loggerConfig.getLevel()) &&
                    this.isMatchType(loggerConfig, type) &&
                    this.isMatchTopic(loggerConfig, topic)
                ) {
                    return loggerConfig.getLevel();
                } else {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    protected <K, V> Level isMatch(LoggerConfig.Type type, String topic, K key, V value) {
        return ListUtils.emptyOnNull(this.clientConfig.getLoggers())
            .stream()
            .map(loggerConfig -> {
                if (logEnabled(loggerConfig.getLevel()) &&
                    this.isMatchType(loggerConfig, type) &&
                    this.isMatchTopic(loggerConfig, topic) &&
                    this.isMatchKey(loggerConfig, key) &&
                    this.isMatchValue(loggerConfig, value)
                ) {
                    return loggerConfig.getLevel();
                } else {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private boolean isMatchType(LoggerConfig loggerConfig, LoggerConfig.Type type) {
        return (
            loggerConfig.getType() == null ||
                loggerConfig.getType() == type
        );
    }

    private boolean isMatchTopic(LoggerConfig loggerConfig, String topic) {
        return (
            loggerConfig.getTopicRegexp() == null ||
                topic.matches(loggerConfig.getTopicRegexp())
        );
    }

    private <K> boolean isMatchKey(LoggerConfig loggerConfig, K key) {
        return (
            loggerConfig.getKeyRegexp() == null ||
                deserialize(key).matches(loggerConfig.getKeyRegexp())
        );
    }

    private <V> boolean isMatchValue(LoggerConfig loggerConfig, V value) {
        return (
            loggerConfig.getValueRegexp() == null ||
                deserialize(value).matches(loggerConfig.getValueRegexp())
        );
    }

    private String deserialize(Object value) {
        if (value instanceof byte[]) {
            return STRING_DESERIALIZER.deserialize("", (byte[])value);
        } else if (value instanceof String) {
            return (String) value;
        } else {
            try {
                return JacksonMapper.ofJson(false).writeValueAsString(value);
            } catch (JsonProcessingException e) {
                return "";
            }
        }
    }

    protected boolean logEnabled(Level level) {
        return (level == Level.TRACE && log.isTraceEnabled()) ||
            (level == Level.DEBUG && log.isDebugEnabled()) ||
            (level == Level.INFO && log.isInfoEnabled()) ||
            (level == Level.WARN && log.isWarnEnabled()) ||
            (level == Level.ERROR && log.isErrorEnabled());
    }

    public void configure(Map<String, ?> configs) {
        ApplicationContext applicationContext = (ApplicationContext) configs.get(KafkaStreamService.APPLICATION_CONTEXT_CONFIG);
        clientConfig = applicationContext.getBean(ClientConfig.class);
    }

    public void close() {

    }

}
