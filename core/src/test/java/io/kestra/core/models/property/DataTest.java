package io.kestra.core.models.property;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.storages.StorageInterface;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@MicronautTest
class DataTest {
    @Inject
    private StorageInterface storage;

    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void shouldNotReadWholeStoredFileWhenFirstRecordIsConsumedOnDemand() throws Exception {
        var records = new StringBuilder();
        for (int i = 0; i < 100_000; i++) {
            records.append("{id:").append(i).append("}\n");
        }
        var content = records.toString().getBytes();
        var uri = storage.put(MAIN_TENANT, null, URI.create("/data-test/records.ion"), new ByteArrayInputStream(content));
        var bytesRead = new AtomicLong();
        var runContext = spy(runContextFactory.of());
        var runContextStorage = spy(runContext.storage());
        doAnswer(invocation -> new FilterInputStream((InputStream) invocation.callRealMethod()) {
            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                var read = super.read(buffer, offset, length);
                if (read > 0) {
                    bytesRead.addAndGet(read);
                }
                return read;
            }
        }).when(runContextStorage).getFile(uri);
        when(runContext.storage()).thenReturn(runContextStorage);

        try (var stream = Data.from(uri.toString()).readOnDemand(runContext).toStream()) {
            assertThat(stream.findFirst()).map(record -> record.get("id")).hasValue(0);
        }

        assertThat(bytesRead.get()).isLessThan(content.length / 4);
    }

    @Test
    void shouldCloseStoredFileWhenOpeningItFails() throws Exception {
        var unchecked = new IllegalStateException("The first read failed.");
        assertThat(readOnDemandWithFirstReadThrowing(unchecked)).isSameAs(unchecked);

        var checked = new IOException("The first read failed.");
        assertThat(readOnDemandWithFirstReadThrowing(checked)).hasCause(checked);
    }

    @Test
    void shouldThrowUncheckedIOExceptionWhenFetchingStoredFileFails() throws Exception {
        var uri = storage.put(MAIN_TENANT, null, URI.create("/data-test/unreadable.ion"), new ByteArrayInputStream("{id:0}\n".getBytes()));
        var failure = new IOException("The stored file can't be fetched.");
        var runContext = spy(runContextFactory.of());
        var runContextStorage = spy(runContext.storage());
        doThrow(failure).when(runContextStorage).getFile(uri);
        when(runContext.storage()).thenReturn(runContextStorage);

        var thrown = catchThrowable(() -> Data.from(uri.toString()).readOnDemand(runContext));

        assertThat(thrown).isInstanceOf(UncheckedIOException.class).hasCause(failure);
    }

    @Test
    void shouldReturnSameRecordsAsReadWhenInlineListIsReadOnDemand() throws Exception {
        var runContext = runContextFactory.of();
        var inline = List.of(Map.of("id", 1), Map.of("id", 2));

        var onDemand = Data.from(inline).readOnDemand(runContext).collectList().block();

        assertThat(onDemand).isEqualTo(Data.from(inline).read(runContext).collectList().block());
        assertThat(onDemand).hasSize(2);
    }

    private Throwable readOnDemandWithFirstReadThrowing(Exception failure) throws Exception {
        var uri = storage.put(MAIN_TENANT, null, URI.create("/data-test/failing.ion"), new ByteArrayInputStream("{id:0}\n".getBytes()));
        var closes = new AtomicInteger();
        var runContext = spy(runContextFactory.of());
        var runContextStorage = spy(runContext.storage());
        doAnswer(invocation -> new InputStream() {
            @Override
            public int read() throws IOException {
                return read(new byte[1], 0, 1);
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                if (failure instanceof IOException ioException) {
                    throw ioException;
                }
                throw (RuntimeException) failure;
            }

            @Override
            public void close() {
                closes.incrementAndGet();
            }
        }).when(runContextStorage).getFile(uri);
        when(runContext.storage()).thenReturn(runContextStorage);

        var thrown = catchThrowable(() -> Data.from(uri.toString()).readOnDemand(runContext));

        assertThat(closes).hasValue(1);
        return thrown;
    }
}
