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
        return isDefault(workerGroup) ? "(default)" : workerGroup;
    }

    /**
     * A worker-group key refers to the default (unnamed) worker group when it is
     * {@code null} or blank.
     *
     * @param key the worker-group key to test
     * @return {@code true} when the key refers to the default worker group
     */
    public static boolean isDefault(String key) {
        return key == null || key.isBlank();
    }
}
