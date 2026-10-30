package io.kestra.core.plugins.endpoint;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogDataStoreInterface;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultTaskRunLogsFetcherTest {
    @Test
    void shouldReturnLogsScopedToExecutionAndTaskRunViaAclFinder() {
        LogDataStoreInterface store = mock(LogDataStoreInterface.class);
        List<LogEntry> expected = List.of(LogEntry.builder().message("m").build());
        when(store.findByExecutionIdAndTaskRunId("main", "exec1", "tr1", Level.INFO)).thenReturn(expected);

        TaskRunLogsFetcher logs = new DefaultTaskRunLogsFetcher(store, "main", "exec1", "tr1");

        assertThat(logs.find(Level.INFO)).isSameAs(expected);
        verify(store).findByExecutionIdAndTaskRunId("main", "exec1", "tr1", Level.INFO);
    }

    @Test
    void shouldDefaultToTraceLevel() {
        LogDataStoreInterface store = mock(LogDataStoreInterface.class);

        new DefaultTaskRunLogsFetcher(store, "main", "exec1", "tr1").find();

        verify(store).findByExecutionIdAndTaskRunId("main", "exec1", "tr1", Level.TRACE);
    }
}
