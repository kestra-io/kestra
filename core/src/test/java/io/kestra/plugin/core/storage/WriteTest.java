package io.kestra.plugin.core.storage;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class WriteTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();

        Write write = Write.builder()
            .content(Property.ofValue("Hello World"))
            .extension(Property.ofValue(".txt"))
            .build();

        var output = write.run(runContext);
        assertThat(output).isNotNull();
        assertThat(output.getUri()).isNotNull();

        InputStream inputStream = storageInterface.get(MAIN_TENANT, null, output.getUri());
        assertThat(inputStream).isNotNull();
        inputStream.close();
    }
}