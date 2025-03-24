package io.kestra.plugin.core.storage;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Random;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
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
            null,
            new URI("/file/storage/get.yml"),
            new ByteArrayInputStream(randomBytes)
        );

        Size bash = Size.builder()
            .uri(Property.of(put.toString()))
            .build();

        Size.Output run = bash.run(runContext);
        assertThat(run.getSize(), is(size));
    }
}
