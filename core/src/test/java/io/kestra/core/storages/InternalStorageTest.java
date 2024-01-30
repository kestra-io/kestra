package io.kestra.core.storages;

import io.kestra.core.models.executions.TaskRun;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class InternalStorageTest {


    @Test
    void shouldGetTaskStateFileFromTaskContext() throws IOException {
        // Given
        StorageInterface storageInterface = Mockito.mock(StorageInterface.class);
        InputStream is = new ByteArrayInputStream(new byte[0]);
        Mockito.when(storageInterface.get(any(), eq(URI.create("/namespace/states/state/name")))).thenReturn(is);
        InternalStorage storage = new InternalStorage(StorageContext.forTask(TaskRun
            .builder()
            .namespace("namespace")
            .flowId("flowid")
            .executionId("executionid")
            .taskId("taskid")
            .id("taskrunid")
            .build()
        ), storageInterface);

        // When
        InputStream result = storage.getTaskStateFile("state", "name", true, true);
        // Then
        Assertions.assertEquals(result, is);
    }

    @Test
    void shouldGetTaskStateFileFromTriggerContext() throws IOException {
        // Given
        StorageInterface storageInterface = Mockito.mock(StorageInterface.class);
        InputStream is = new ByteArrayInputStream(new byte[0]);
        Mockito.when(storageInterface.get(any(), eq(URI.create("/namespace/states/state/name")))).thenReturn(is);
        InternalStorage storage = new InternalStorage(StorageContext.forTrigger(null,
            "namespace",
            "flowid",
            "executionId",
            "triggerId"), storageInterface);

        // When
        InputStream result = storage.getTaskStateFile("state", "name", true, true);

        // Then
        Assertions.assertEquals(result, is);
    }
}