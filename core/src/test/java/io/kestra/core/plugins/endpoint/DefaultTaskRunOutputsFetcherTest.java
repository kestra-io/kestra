package io.kestra.core.plugins.endpoint;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.services.TaskOutputService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultTaskRunOutputsFetcherTest {
    private static final TaskRun TASK_RUN = TaskRun.builder().id("tr1").build();

    @Test
    void shouldReturnOutputsFromTaskOutputService() throws Exception {
        TaskOutputService taskOutputService = mock(TaskOutputService.class);
        when(taskOutputService.getOutputs(TASK_RUN)).thenReturn(Map.of("a", 1));

        TaskRunOutputsFetcher outputs = new DefaultTaskRunOutputsFetcher(taskOutputService, TASK_RUN);

        assertThat(outputs.get()).containsExactlyEntriesOf(Map.of("a", 1));
    }

    @Test
    void shouldWrapInternalException() throws Exception {
        TaskOutputService taskOutputService = mock(TaskOutputService.class);
        when(taskOutputService.getOutputs(TASK_RUN)).thenThrow(new InternalException("boom"));

        TaskRunOutputsFetcher outputs = new DefaultTaskRunOutputsFetcher(taskOutputService, TASK_RUN);

        assertThatThrownBy(outputs::get).isInstanceOf(KestraRuntimeException.class);
    }
}
