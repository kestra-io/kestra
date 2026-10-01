package io.kestra.core.plugins.endpoint;

import io.kestra.core.storages.StorageInterface;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScopedExecutionStorageTest {
    private static final String TENANT = "main";
    private static final String NAMESPACE = "io.kestra.test";
    private static final String FLOW = "myflow";
    private static final String EXECUTION = "exec1";
    // execution prefix path: io/kestra/test/myflow/executions/exec1

    private ScopedExecutionStorage storage(StorageInterface delegate) {
        return new ScopedExecutionStorage(delegate, TENANT, NAMESPACE, FLOW, EXECUTION);
    }

    @Test
    void shouldReadFileInsideExecutionPrefix() throws Exception {
        StorageInterface delegate = mock(StorageInterface.class);
        URI uri = URI.create("kestra:///io/kestra/test/myflow/executions/exec1/tasks/t/tr/out.txt");
        InputStream stream = new ByteArrayInputStream("data".getBytes());
        when(delegate.get(eq(TENANT), eq(NAMESPACE), eq(uri))).thenReturn(stream);

        assertThat(storage(delegate).getFile(uri)).isSameAs(stream);
        verify(delegate).get(TENANT, NAMESPACE, uri);
    }

    @Test
    void shouldRejectFileInAnotherExecution() throws Exception {
        StorageInterface delegate = mock(StorageInterface.class);
        URI uri = URI.create("kestra:///io/kestra/test/myflow/executions/exec2/out.txt");

        assertThatThrownBy(() -> storage(delegate).getFile(uri))
            .isInstanceOf(IllegalArgumentException.class);
        verify(delegate, never()).get(any(), any(), any());
    }

    @Test
    void shouldRejectPrefixSiblingExecution() throws Exception {
        StorageInterface delegate = mock(StorageInterface.class);
        URI uri = URI.create("kestra:///io/kestra/test/myflow/executions/exec1x/out.txt");

        assertThatThrownBy(() -> storage(delegate).getFile(uri))
            .isInstanceOf(IllegalArgumentException.class);
        verify(delegate, never()).get(any(), any(), any());
    }

    @Test
    void shouldRejectParentTraversal() throws Exception {
        StorageInterface delegate = mock(StorageInterface.class);
        URI uri = URI.create("kestra:///io/kestra/test/myflow/executions/exec1/../exec2/out.txt");

        assertThatThrownBy(() -> storage(delegate).getFile(uri))
            .isInstanceOf(IllegalArgumentException.class);
        verify(delegate, never()).get(any(), any(), any());
    }

    @Test
    void shouldRejectWriteOutsideExecutionPrefix() throws Exception {
        StorageInterface delegate = mock(StorageInterface.class);
        URI uri = URI.create("kestra:///io/kestra/test/otherflow/executions/exec1/out.txt");

        assertThatThrownBy(() -> storage(delegate).putFile(uri, new ByteArrayInputStream(new byte[0])))
            .isInstanceOf(IllegalArgumentException.class);
        verify(delegate, never()).put(any(String.class), any(String.class), any(URI.class), any(InputStream.class));
    }

    @Test
    void shouldRejectListOutsideExecutionPrefix() throws Exception {
        StorageInterface delegate = mock(StorageInterface.class);
        URI uri = URI.create("kestra:///io/kestra/test/myflow/executions/exec2/out.txt");

        assertThatThrownBy(() -> storage(delegate).list(uri))
            .isInstanceOf(IllegalArgumentException.class);
        verify(delegate, never()).list(any(), any(), any());
    }

    @Test
    void shouldRejectExistsOutsideExecutionPrefix() {
        StorageInterface delegate = mock(StorageInterface.class);
        URI uri = URI.create("kestra:///io/kestra/test/myflow/executions/exec2/out.txt");

        assertThatThrownBy(() -> storage(delegate).exists(uri))
            .isInstanceOf(IllegalArgumentException.class);
        verify(delegate, never()).exists(any(), any(), any());
    }

    @Test
    void shouldRejectDeleteOutsideExecutionPrefix() throws Exception {
        StorageInterface delegate = mock(StorageInterface.class);
        URI uri = URI.create("kestra:///io/kestra/test/myflow/executions/exec2/out.txt");

        assertThatThrownBy(() -> storage(delegate).deleteFile(uri))
            .isInstanceOf(IllegalArgumentException.class);
        verify(delegate, never()).delete(any(), any(), any());
    }

    @Test
    void shouldRejectNullUri() throws Exception {
        StorageInterface delegate = mock(StorageInterface.class);

        assertThatThrownBy(() -> storage(delegate).getFile(null))
            .isInstanceOf(IllegalArgumentException.class);
        verify(delegate, never()).get(any(), any(), any());
    }
}
