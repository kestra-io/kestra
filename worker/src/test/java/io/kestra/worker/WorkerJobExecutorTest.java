package io.kestra.worker;

import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTrigger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class WorkerJobExecutorTest {

    @Test
    void shouldIdentifyRealtimeTriggerJobWhenTriggerIsRealtime() {
        // Given a worker trigger whose trigger is a realtime (streaming) trigger
        WorkerTrigger realtime = mock(WorkerTrigger.class);
        AbstractTrigger trigger = mock(AbstractTrigger.class, withSettings().extraInterfaces(RealtimeTriggerInterface.class));
        when(realtime.getTrigger()).thenReturn(trigger);

        // When-Then
        assertThat(WorkerJobExecutor.isRealtimeTriggerJob(realtime)).isTrue();
    }

    @Test
    void shouldNotIdentifyPollingTriggerOrTaskAsRealtime() {
        // Given a polling trigger and a task
        WorkerTrigger polling = mock(WorkerTrigger.class);
        when(polling.getTrigger()).thenReturn(mock(AbstractTrigger.class));
        WorkerTask task = mock(WorkerTask.class);

        // When-Then
        assertThat(WorkerJobExecutor.isRealtimeTriggerJob(polling)).isFalse();
        assertThat(WorkerJobExecutor.isRealtimeTriggerJob(task)).isFalse();
    }
}
