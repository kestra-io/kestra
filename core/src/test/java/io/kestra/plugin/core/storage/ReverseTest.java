package io.kestra.plugin.core.storage;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.common.io.CharStreams;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.Storage;
import io.kestra.core.storages.StorageInterface;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@KestraTest
class ReverseTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();

        URI put = storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/file/storage/get.yml"),
            new ByteArrayInputStream("1\n2\n3\n".getBytes())
        );

        Reverse result = Reverse.builder()
            .from(Property.ofValue(put.toString()))
            .build();

        Reverse.Output run = result.run(runContext);

        assertThat(run.getUri().getPath()).endsWith(".yml");
        try (InputStreamReader reader = new InputStreamReader(storageInterface.get(MAIN_TENANT, null, run.getUri()))) {
            assertThat(CharStreams.toString(reader)).isEqualTo("3\n2\n1\n");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void shouldCloseSourceAfterSuccessfulOrFailedCopy(boolean failCopy) throws Exception {
        URI source;
        try (InputStream input = new ByteArrayInputStream("1\n2\n3\n".getBytes(StandardCharsets.UTF_8))) {
            source = storageInterface.put(MAIN_TENANT, null, URI.create("/file/storage/reverse-resource.txt"), input);
        }
        RunContext context = spy(runContextFactory.of());
        Storage storage = spy(context.storage());
        doReturn(storage).when(context).storage();
        AtomicBoolean closed = new AtomicBoolean();
        InputStream input = new FilterInputStream(storage.getFile(source)) {
            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                if (failCopy)
                    throw new IOException("copy failed");
                return super.read(bytes, offset, length);
            }

            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
        doReturn(input).when(storage).getFile(source);
        Reverse task = Reverse.builder().from(Property.ofValue(source.toString())).build();
        try {
            if (failCopy) {
                assertThatThrownBy(() -> task.run(context)).isInstanceOf(IOException.class).hasMessage("copy failed");
            } else {
                Reverse.Output result = task.run(context);
                try (InputStream output = storageInterface.get(MAIN_TENANT, null, result.getUri())) {
                    assertThat(new String(output.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("3\n2\n1\n");
                }
            }
            assertThat(closed.get()).as("source stream must be closed before task returns or fails").isTrue();
        } finally {
            input.close();
        }
    }

    @Test
    void closesReverseReader() throws Exception {
        RunContext context = runContextFactory.of();
        URI source;
        try (InputStream input = new ByteArrayInputStream("1\n2\n3\n".getBytes(StandardCharsets.UTF_8))) {
            source = storageInterface.put(MAIN_TENANT, null, URI.create("/file/storage/reverse-reader.txt"), input);
        }
        Reverse task = Reverse.builder().from(Property.ofValue(source.toString())).build();
        try (var readers = mockConstruction(ReversedLinesFileReader.class)) {
            task.run(context);
            assertThat(readers.constructed()).hasSize(1);
            verify(readers.constructed().getFirst()).close();
        }
    }
}
