package io.kestra.core.services;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.services.configuration.TaskOutputConfiguration;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.InternalStorage;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@KestraTest
class TaskOutputCopyTest {
    @Inject
    TaskOutputRepositoryInterface repository;
    @Inject
    StorageInterface storage;
    @Inject
    NamespaceFactory namespaceFactory;

    private final List<TaskRun> taskRuns = new ArrayList<>();
    private TaskOutputService service;
    private String tenant;

    @BeforeEach
    void setUp() {
        tenant = TestsUtils.randomTenant("output-copy");
        service = new TaskOutputService(repository, storage, namespaceFactory, new TaskOutputConfiguration(256));
    }

    @AfterEach
    void cleanUp() throws IOException {
        for (TaskRun taskRun : taskRuns) {
            storage.deleteByPrefix(tenant, taskRun.getNamespace(), StorageContext.forTask(taskRun).getExecutionStorageURI());
        }
        repository.purgeByExecutionIds(taskRuns.stream().map(TaskRun::getExecutionId).toList());
    }

    @Test
    void shouldPreserveOffloadedOutputsWhenOriginalStorageIsDeleted() throws Exception {
        TaskRun original = taskRun();
        TaskRun copied = taskRun();
        var originalStorage = new InternalStorage(StorageContext.forTask(original), storage, namespaceFactory);
        URI userFile = originalStorage.putFile(new ByteArrayInputStream(new byte[] { 1, 2, 3 }), "user-file.bin");
        Map<String, Object> values = Map.of(
            "value", "snapshot-".repeat(128),
            "metadata", Map.of("batch", "synthetic", "file", userFile.toString()),
            "items", List.of("first", "second")
        );
        service.saveOutputs(original, values);
        TaskOutput originalOutput = output(original);
        byte[] originalBytes;
        try (var input = originalStorage.getFile(URI.create(originalOutput.uri()))) {
            originalBytes = input.readAllBytes();
        }

        service = new TaskOutputService(repository, storage, namespaceFactory, new TaskOutputConfiguration(-1));
        service.copyOutputs(original, copied);

        TaskOutput copiedOutput = output(copied);
        assertThat(copiedOutput.executionId()).isEqualTo(copied.getExecutionId());
        assertThat(copiedOutput.value()).isNull();
        assertThat(copiedOutput.uri()).isNotEqualTo(originalOutput.uri());
        assertThat(URI.create(copiedOutput.uri()).getPath()).startsWith(StorageContext.forTask(copied).getContextStorageURI().getPath() + "/");
        try (var input = storage.get(tenant, copied.getNamespace(), URI.create(copiedOutput.uri()))) {
            assertThat(input.readAllBytes()).isEqualTo(originalBytes);
        }
        assertThat(service.getOutputs(original)).isEqualTo(values);
        assertThat(storage.list(tenant, copied.getNamespace(), StorageContext.forTask(copied).getContextStorageURI())).hasSize(1);

        originalStorage.deleteExecutionFiles();

        assertThat(storage.exists(tenant, original.getNamespace(), userFile)).isFalse();
        assertThat(service.getOutputs(copied)).isEqualTo(values);
    }

    @Test
    void shouldPreserveLatestCopyWhenEarlierExecutionsAreDeleted() throws Exception {
        TaskRun original = taskRun();
        TaskRun firstCopy = taskRun();
        TaskRun lastCopy = taskRun();
        Map<String, Object> values = Map.of("value", "snapshot-".repeat(128));
        service.saveOutputs(original, values);
        service.copyOutputs(original, firstCopy);
        service.copyOutputs(firstCopy, lastCopy);

        storage.deleteByPrefix(tenant, original.getNamespace(), StorageContext.forTask(original).getExecutionStorageURI());
        storage.deleteByPrefix(tenant, firstCopy.getNamespace(), StorageContext.forTask(firstCopy).getExecutionStorageURI());

        assertThat(service.getOutputs(lastCopy)).isEqualTo(values);
        assertThat(output(lastCopy).uri()).contains(lastCopy.getExecutionId()).doesNotContain(original.getExecutionId());
    }

    @Test
    void shouldCopyInlineOutputsWithoutAccessingStorage() throws Exception {
        TaskRun original = taskRun();
        TaskRun copied = taskRun();
        Map<String, Object> values = Map.of("value", "inline");
        service.saveOutputs(original, values);
        StorageInterface unusedStorage = mock(StorageInterface.class);
        var copyService = new TaskOutputService(repository, unusedStorage, namespaceFactory, new TaskOutputConfiguration(0));

        copyService.copyOutputs(original, copied);

        assertThat(output(copied).value()).isEqualTo(output(original).value());
        assertThat(output(copied).uri()).isNull();
        assertThat(copyService.getOutputs(copied)).isEqualTo(values);
        verifyNoInteractions(unusedStorage);
    }

    @Test
    void shouldLeaveDestinationEmptyWhenSourceHasNoOutputRecord() {
        TaskRun original = taskRun();
        TaskRun copied = taskRun();
        StorageInterface unusedStorage = mock(StorageInterface.class);
        var copyService = new TaskOutputService(repository, unusedStorage, namespaceFactory, new TaskOutputConfiguration(256));

        copyService.copyOutputs(original, copied);

        assertThat(repository.findById(tenant, copied.getId())).isEmpty();
        verifyNoInteractions(unusedStorage);
    }

    @Test
    void shouldCopyEmptyOutputRecordWithoutAccessingStorage() throws Exception {
        TaskRun original = taskRun();
        TaskRun copied = taskRun();
        repository.save(new TaskOutput(original.getId(), tenant, original.getExecutionId(), null, null));
        StorageInterface unusedStorage = mock(StorageInterface.class);
        var copyService = new TaskOutputService(repository, unusedStorage, namespaceFactory, new TaskOutputConfiguration(256));

        copyService.copyOutputs(original, copied);

        assertThat(output(copied).value()).isNull();
        assertThat(output(copied).uri()).isNull();
        assertThat(copyService.getOutputs(copied)).isEmpty();
        verifyNoInteractions(unusedStorage);
    }

    @Test
    void shouldFailWithoutSavingWhenSourceBlobIsMissing() throws Exception {
        CopyFixture fixture = copyFixture();
        var failure = new FileNotFoundException("The source output blob is missing.");
        when(fixture.storage().getAttributes(eq(tenant), any(), eq(fixture.sourceUri()))).thenThrow(failure);

        assertCopyFailure(fixture, failure);

        verify(fixture.storage(), never()).get(eq(tenant), any(), any());
    }

    @Test
    void shouldFailWithoutSavingWhenReadingSourceFails() throws Exception {
        CopyFixture fixture = copyFixture();
        var failure = new IOException("Cannot read the source output blob.");
        InputStream input = spy(new InputStream() {
            @Override
            public int read() throws IOException {
                throw failure;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                throw failure;
            }
        });
        when(fixture.storage().get(eq(tenant), any(), eq(fixture.sourceUri()))).thenReturn(input);

        assertCopyFailure(fixture, failure);

        verify(input).close();
    }

    @Test
    void shouldFailWithoutSavingWhenWritingDestinationFails() throws Exception {
        CopyFixture fixture = copyFixture();
        var failure = new IOException("Cannot write the destination output blob.");
        when(fixture.storage().put(eq(tenant), any(), any(URI.class), any(InputStream.class))).thenThrow(failure);

        assertCopyFailure(fixture, failure);

        verify(fixture.input()).close();
    }

    @Test
    void shouldFailWithoutSavingWhenSourceStreamCannotBeClosed() throws Exception {
        CopyFixture fixture = copyFixture();
        var failure = new IOException("Cannot close the source output stream.");
        doThrow(failure).when(fixture.input()).close();

        assertCopyFailure(fixture, failure);
    }

    @Test
    void shouldFailWithoutSavingWhenRetriedWriteStoresOnlyRemainingBytes() throws Exception {
        CopyFixture fixture = copyFixture();
        FileAttributes shortCopy = mock(FileAttributes.class);
        FileAttributes original = fixture.storage().getAttributes(tenant, fixture.original().getNamespace(), fixture.sourceUri());
        when(fixture.storage().getAttributes(eq(tenant), any(), any(URI.class))).thenReturn(original, shortCopy);
        when(fixture.storage().put(eq(tenant), any(), any(URI.class), any(InputStream.class))).thenAnswer(invocation ->
        {
            InputStream data = invocation.getArgument(3);
            data.readNBytes(3);
            byte[] remaining = data.readAllBytes();
            when(shortCopy.getSize()).thenReturn((long) remaining.length);
            URI requested = invocation.getArgument(2);
            return URI.create(StorageContext.KESTRA_PROTOCOL + requested.getPath());
        });

        assertThatThrownBy(() -> fixture.service().copyOutputs(fixture.original(), fixture.copied()))
            .isInstanceOf(KestraRuntimeException.class)
            .hasRootCauseInstanceOf(IOException.class)
            .hasRootCauseMessage("Cannot copy the output blob: expected 4 bytes but stored 1.");

        verify(fixture.repository(), never()).save(any());
        verify(fixture.input()).close();
    }

    @Test
    void shouldKeepCopiedBlobWhenRepositorySaveFails() throws Exception {
        CopyFixture fixture = copyFixture();
        var failure = new IllegalStateException("The repository save outcome is unknown.");
        when(fixture.repository().save(any())).thenThrow(failure);

        assertThatThrownBy(() -> fixture.service().copyOutputs(fixture.original(), fixture.copied())).isSameAs(failure);

        verify(fixture.storage(), never()).delete(eq(tenant), any(), any());
        verify(fixture.input()).close();
    }

    private void assertCopyFailure(CopyFixture fixture, IOException failure) {
        assertThatThrownBy(() -> fixture.service().copyOutputs(fixture.original(), fixture.copied()))
            .isInstanceOf(KestraRuntimeException.class)
            .hasRootCauseInstanceOf(failure.getClass())
            .hasRootCauseMessage(failure.getMessage());
        verify(fixture.repository(), never()).save(any());
    }

    private CopyFixture copyFixture() throws IOException {
        TaskRun original = taskRun();
        TaskRun copied = taskRun();
        URI sourceUri = URI.create(StorageContext.KESTRA_PROTOCOL + StorageContext.forTask(original).getContextStorageURI().getPath() + "/output.ion");
        StorageInterface mockStorage = mock(StorageInterface.class);
        TaskOutputRepositoryInterface mockRepository = mock(TaskOutputRepositoryInterface.class);
        InputStream input = spy(new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 }));
        FileAttributes attributes = mock(FileAttributes.class);
        when(attributes.getSize()).thenReturn(4L);
        when(mockStorage.getAttributes(eq(tenant), any(), any(URI.class))).thenReturn(attributes);
        when(mockStorage.get(eq(tenant), any(), eq(sourceUri))).thenReturn(input);
        when(mockStorage.put(eq(tenant), any(), any(URI.class), any(InputStream.class))).thenAnswer(invocation ->
        {
            InputStream data = invocation.getArgument(3);
            data.transferTo(OutputStream.nullOutputStream());
            URI requested = invocation.getArgument(2);
            return URI.create(StorageContext.KESTRA_PROTOCOL + requested.getPath());
        });
        when(mockRepository.findById(tenant, original.getId()))
            .thenReturn(Optional.of(new TaskOutput(original.getId(), tenant, original.getExecutionId(), null, sourceUri.toString())));
        var copyService = new TaskOutputService(mockRepository, mockStorage, namespaceFactory, new TaskOutputConfiguration(256));
        return new CopyFixture(copyService, mockStorage, mockRepository, original, copied, sourceUri, input);
    }

    private TaskOutput output(TaskRun taskRun) {
        return repository.findById(tenant, taskRun.getId()).orElseThrow();
    }

    private TaskRun taskRun() {
        TaskRun taskRun = TaskRun.builder()
            .id(IdUtils.create())
            .tenantId(tenant)
            .executionId(IdUtils.create())
            .namespace("io.kestra.tests")
            .flowId("output-copy")
            .taskId("first")
            .state(new State(State.Type.SUCCESS))
            .build();
        taskRuns.add(taskRun);
        return taskRun;
    }

    private record CopyFixture(TaskOutputService service, StorageInterface storage, TaskOutputRepositoryInterface repository,
        TaskRun original, TaskRun copied, URI sourceUri, InputStream input) {
    }
}
