package io.kestra.plugin.core.storage;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import org.junit.jupiter.api.Test;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;

import java.io.FileInputStream;
import java.net.URI;
import java.net.URL;
import java.util.NoSuchElementException;
import java.util.Objects;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class DeleteTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();
        URL resource = DeleteTest.class.getClassLoader().getResource("application-test.yml");

        URI put = storageInterface.put(
            null,
            null,
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );


        Delete bash = Delete.builder()
            .uri(Property.of(put.toString()))
            .build();

        Delete.Output run = bash.run(runContext);
        assertThat(run.getDeleted(), is(true));

        run = bash.run(runContext);
        assertThat(run.getDeleted(), is(false));

        assertThrows(NoSuchElementException.class, () -> {
            Delete error = Delete.builder()
                .uri(Property.of(put.toString()))
                .errorOnMissing(Property.of(true))
                .build();

            error.run(runContext);
        });
    }
}
