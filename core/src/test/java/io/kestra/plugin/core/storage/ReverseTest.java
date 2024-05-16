package io.kestra.plugin.core.storage;

import com.google.common.io.CharStreams;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;

@MicronautTest
class ReverseTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();

        URI put = storageInterface.put(
            null,
            new URI("/file/storage/get.yml"),
            new ByteArrayInputStream("1\n2\n3\n".getBytes())
        );


        Reverse result = Reverse.builder()
            .from(put.toString())
            .build();

        Reverse.Output run = result.run(runContext);

        assertThat(run.getUri().getPath(), endsWith(".yml"));
        assertThat(CharStreams.toString(new InputStreamReader(storageInterface.get(null, run.getUri()))), is("3\n2\n1\n"));
    }
}