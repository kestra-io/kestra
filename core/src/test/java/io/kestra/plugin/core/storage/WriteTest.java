package io.kestra.plugin.core.storage;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

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
            .content(Property.of("Hello World"))
            .extension(Property.of(".txt"))
            .build();

        var output = write.run(runContext);
        assertThat(output, notNullValue());
        assertThat(output.getUri(), notNullValue());

        InputStream inputStream = storageInterface.get(null, null, output.getUri());
        assertThat(inputStream, notNullValue());
        inputStream.close();
    }
}