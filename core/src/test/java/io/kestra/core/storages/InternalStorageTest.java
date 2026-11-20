package io.kestra.core.storages;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import io.kestra.core.exceptions.ResourceAccessDeniedException;
import io.kestra.core.services.NamespaceService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalStorageTest {
    private static final String TENANT = "tenant";
    private static final String CALLER_NAMESPACE = "company.team";

    private StorageInterface storageInterface;
    private NamespaceService namespaceService;

    @BeforeEach
    void setUp() throws IOException {
        storageInterface = mock(StorageInterface.class);
        namespaceService = mock(NamespaceService.class);
        when(storageInterface.get(any(), any(), any())).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(storageInterface.exists(any(), any(), any())).thenReturn(true);
        when(namespaceService.isAllowedNamespace(any(), any(), any(), any())).thenReturn(false);
    }

    private void denyNamespace(String namespace) {
        when(namespaceService.isNamespaceExists(TENANT, namespace)).thenReturn(true);
        doThrow(new ResourceAccessDeniedException("Namespace %s is not allowed.".formatted(namespace)))
            .when(namespaceService).checkAllowedNamespace(eq(TENANT), eq(namespace), eq(TENANT), eq(CALLER_NAMESPACE));
    }

    private InternalStorage storage(String triggeringExecutionPrefix) {
        return new InternalStorage(
            LoggerFactory.getLogger(InternalStorageTest.class),
            StorageContext.forTask(TENANT, "company.team", "my-flow", "exec1", "task", "taskrun", null),
            storageInterface,
            namespaceService,
            mock(NamespaceFactory.class),
            triggeringExecutionPrefix
        );
    }

    @Test
    void shouldNotCheckNamespaceForOwnFlowStorage() {
        InternalStorage storage = storage(null);

        assertThatCode(() -> {
            storage.getFile(URI.create("kestra:///company/team/my-flow/executions/oldexec/tasks/task/run/out.ion"));
            storage.deleteFile(URI.create("kestra:///company/team/my-flow/company/cache/cache.zip"));
            storage.putFile(new ByteArrayInputStream(new byte[0]), URI.create("kestra:///company/team/_files/a.txt"));
            storage.getFile(URI.create("kestra:///company/team/states/key"));
        }).doesNotThrowAnyException();

        verify(namespaceService, never()).isAllowedNamespace(any(), any(), any(), any());
    }

    @Test
    void shouldNotCheckNamespaceForTriggeringExecution() {
        InternalStorage storage = storage("/other/ns/parent-flow/executions/parent1/");

        assertThatCode(() -> storage.getFile(URI.create("kestra:///other/ns/parent-flow/executions/parent1/inputs/file/a.txt")))
            .doesNotThrowAnyException();

        verify(namespaceService, never()).isAllowedNamespace(any(), any(), any(), any());
    }

    @Test
    void shouldDenyAnyAccessToANamespaceThatIsNotAllowed() throws IOException {
        URI foreign = URI.create("kestra:///victim/flow/executions/exec/tasks/task/run/out.ion");
        denyNamespace("victim");
        InternalStorage storage = storage(null);

        assertThatThrownBy(() -> storage.getFile(foreign)).isInstanceOf(ResourceAccessDeniedException.class);
        assertThatThrownBy(() -> storage.getAttributes(foreign)).isInstanceOf(ResourceAccessDeniedException.class);
        assertThatThrownBy(() -> storage.deleteFile(foreign)).isInstanceOf(ResourceAccessDeniedException.class);
        assertThatThrownBy(() -> storage.putFile(new ByteArrayInputStream(new byte[0]), foreign)).isInstanceOf(ResourceAccessDeniedException.class);
        assertThatThrownBy(() -> storage.isFileExist(foreign)).isInstanceOf(ResourceAccessDeniedException.class);
        assertThatThrownBy(() -> storage.list(foreign)).isInstanceOf(ResourceAccessDeniedException.class);
        verify(storageInterface, never()).put(any(), any(), any(), any(InputStream.class));
        verify(storageInterface, never()).delete(any(), any(), any());
    }

    @Test
    void shouldCheckEveryNamespaceTheForeignPathMayBelongTo() {
        // "victim.executions" is a valid namespace name, so the path alone cannot tell where the namespace stops
        URI ambiguous = URI.create("kestra:///victim/executions/flow/executions/exec/tasks/task/run/out.ion");
        denyNamespace("victim.executions");

        assertThatThrownBy(() -> storage(null).getFile(ambiguous)).isInstanceOf(ResourceAccessDeniedException.class);
    }

    @Test
    void shouldNotCheckParentNamespacesOfTheCaller() {
        assertThatCode(() -> storage(null).getFile(URI.create("kestra:///company/team/other-flow/executions/exec/tasks/task/run/out.ion")))
            .doesNotThrowAnyException();

        verify(namespaceService, never()).isAllowedNamespace(any(), eq("company"), any(), any());
        verify(namespaceService, never()).isAllowedNamespace(any(), eq("company.team"), any(), any());
    }

    @Test
    void shouldRejectParentTraversalOutOfTheOwnFlowStorage() {
        URI traversal = URI.create("kestra:///company/team/my-flow/executions/../../../victim/flow/executions/e/out.ion");
        InternalStorage storage = storage(null);

        assertThatThrownBy(() -> storage.getFile(traversal)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.list(traversal)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.isFileExist(traversal)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.putFile(new ByteArrayInputStream(new byte[0]), traversal)).isInstanceOf(IllegalArgumentException.class);
    }
}
