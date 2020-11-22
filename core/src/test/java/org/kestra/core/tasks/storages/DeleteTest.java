package org.kestra.core.tasks.storages;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunContextFactory;
import org.kestra.core.storages.StorageInterface;

import java.io.FileInputStream;
import java.net.URI;
import java.net.URL;
import java.util.NoSuchElementException;
import java.util.Objects;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class DeleteTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();
        URL resource = DeleteTest.class.getClassLoader().getResource("application.yml");

        URI put = storageInterface.put(
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );


        Delete bash = Delete.builder()
            .uri(put.toString())
            .build();

        Delete.Output run = bash.run(runContext);
        assertThat(run.getDeleted(), is(true));

        run = bash.run(runContext);
        assertThat(run.getDeleted(), is(false));

        assertThrows(NoSuchElementException.class, () -> {
            Delete error = Delete.builder()
                .uri(put.toString())
                .errorOnMissing(true)
                .build();

            error.run(runContext);
        });
    }
}
