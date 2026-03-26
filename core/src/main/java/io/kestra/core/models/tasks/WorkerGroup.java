package io.kestra.core.models.tasks;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WorkerGroup {

    private String key;

    private Fallback fallback;

    public enum Fallback {
        FAIL,
        WAIT,
        CANCEL,
    }

    /**
     * Format worker group for log display
     *
     * @param workerGroup the worker group
     * @return formatted worker group
     */
    public static String forLog(String workerGroup) {
        return workerGroup == null || workerGroup.isEmpty() ? "(default)" : workerGroup;
    }
}
