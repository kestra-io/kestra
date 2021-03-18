package io.kestra.core.tasks.storages;

import com.google.common.io.CharStreams;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Objects;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class ConcatTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of();
        URL resource = ConcatTest.class.getClassLoader().getResource("application.yml");

        File file = new File(Objects.requireNonNull(ConcatTest.class.getClassLoader()
            .getResource("application.yml"))
            .toURI());

        URI put = storageInterface.put(
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );

        Concat result = Concat.builder()
            .files(Arrays.asList(put.toString(), put.toString()))
            .separator("\n")
            .build();

        Concat.Output run = result.run(runContext);
        String s = CharStreams.toString(new InputStreamReader(new FileInputStream(file)));


        assertThat(
            CharStreams.toString(new InputStreamReader(storageInterface.get(run.getUri()))),
            is(s + "\n" + s + "\n")
        );
    }
}
