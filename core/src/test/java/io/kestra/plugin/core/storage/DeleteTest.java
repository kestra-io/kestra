package io.kestra.plugin.core.storage;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import org.junit.jupiter.api.Test;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.StorageInterface;

import java.io.FileInputStream;
import java.net.URI;
import java.net.URL;
import java.util.NoSuchElementException;
import java.util.Objects;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class DeleteTest {
    @Inject
    TestRunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();
        URL resource = DeleteTest.class.getClassLoader().getResource("application-test.yml");

        URI put = storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );


        Delete bash = Delete.builder()
            .uri(Property.ofValue(put.toString()))
            .build();

        Delete.Output run = bash.run(runContext);
        assertThat(run.getDeleted()).isTrue();

        run = bash.run(runContext);
        assertThat(run.getDeleted()).isFalse();

        assertThrows(NoSuchElementException.class, () -> {
            Delete error = Delete.builder()
                .uri(Property.ofValue(put.toString()))
                .errorOnMissing(Property.ofValue(true))
                .build();

            error.run(runContext);
        });
    }
}
