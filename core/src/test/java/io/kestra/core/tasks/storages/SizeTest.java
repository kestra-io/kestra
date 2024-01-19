package io.kestra.core.tasks.storages;

import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.net.URI;
import java.net.URL;
import java.util.Objects;
import java.util.Random;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class SizeTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();

        final Long size = 42L;
        byte[] randomBytes = new byte[size.intValue()];
        new Random().nextBytes(randomBytes);

        URI put = storageInterface.put(
            null,
            new URI("/file/storage/get.yml"),
            new ByteArrayInputStream(randomBytes)
        );

        Size bash = Size.builder()
            .uri(put.toString())
            .build();

        Size.Output run = bash.run(runContext);
        assertThat(run.getSize(), is(size));
    }
}
