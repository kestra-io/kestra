package io.kestra.core.models.tasks.logs;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class LogRecord {

    String resource;
    long timestampEpochNanos;
    String severity;
    Map<String, Object> attributes;
    String bodyValue;

}
