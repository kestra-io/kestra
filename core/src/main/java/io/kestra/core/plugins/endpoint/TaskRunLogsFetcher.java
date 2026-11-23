package io.kestra.core.plugins.endpoint;

import io.kestra.core.models.executions.LogEntry;
import org.slf4j.event.Level;

import java.util.List;

/**
 * Read-only access to the logs of the single taskRun a plugin endpoint was invoked for. Backed by the
 * access-controlled log finder; a plugin holding this handle cannot read another taskRun's or execution's logs.
 */
public interface TaskRunLogsFetcher {
    List<LogEntry> find(Level minLevel);

    default List<LogEntry> find() {
        return find(Level.TRACE);
    }
}
