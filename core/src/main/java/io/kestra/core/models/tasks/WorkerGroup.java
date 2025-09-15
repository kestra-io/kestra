package io.kestra.core.models.tasks;

import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Introspected
public class WorkerGroup {

    private String key;

    private Fallback fallback;

    public enum Fallback {
        FAIL,
        WAIT,
        CANCEL,
    }
}
