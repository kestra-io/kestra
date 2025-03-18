package io.kestra.plugin.core.storage;

import com.google.common.io.CharStreams;
import io.kestra.core.models.property.Property;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.junit.annotations.KestraTest;
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
import java.util.List;
import java.util.Objects;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;

@KestraTest
class ConcatTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    void run(Boolean json) throws Exception {
        RunContext runContext = runContextFactory.of();
        URL resource = ConcatTest.class.getClassLoader().getResource("application-test.yml");

        File file = new File(Objects.requireNonNull(ConcatTest.class.getClassLoader()
            .getResource("application-test.yml"))
            .toURI());

        URI put = storageInterface.put(
            null,
            null,
            new URI("/file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );

        List<String> files = Arrays.asList(put.toString(), put.toString());

        Concat result = Concat.builder()
            .files(json ? JacksonMapper.ofJson().writeValueAsString(files) : files)
            .separator(Property.of("\n"))
            .extension(Property.of(".yml"))
            .build();

        Concat.Output run = result.run(runContext);
        String s = CharStreams.toString(new InputStreamReader(new FileInputStream(file)));


        assertThat(
            CharStreams.toString(new InputStreamReader(storageInterface.get(null, null, run.getUri()))),
            is(s + "\n" + s + "\n")
        );
        assertThat(run.getUri().getPath(), endsWith(".yml"));
    }

    @Test
    void list() throws Exception {
        this.run(false);
    }

    @Test
    void json() throws Exception {
        this.run(true);
    }
}
