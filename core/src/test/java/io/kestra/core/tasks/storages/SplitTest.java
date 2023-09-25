package io.kestra.core.tasks.storages;

import com.google.common.io.CharStreams;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.Rethrow;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;

@MicronautTest
class SplitTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void partition() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUpload(1000);

        Split result = Split.builder()
            .from(put.toString())
            .partitions(8)
            .build();

        Split.Output run = result.run(runContext);

        assertThat(run.getUris().size(), is(8));
        assertThat(run.getUris().get(0).getPath(), endsWith(".yml"));
        assertThat(StringUtils.countMatches(readAll(run.getUris()), "\n"), is(1000));
    }

    @Test
    void rows() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUpload(1000);

        Split result = Split.builder()
            .from(put.toString())
            .rows(10)
            .build();

        Split.Output run = result.run(runContext);

        assertThat(run.getUris().size(), is(100));
        assertThat(readAll(run.getUris()), is(String.join("\n", content(1000)) + "\n"));
    }

    @Test
    void bytes() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUpload(12288);

        Split result = Split.builder()
            .from(put.toString())
            .bytes("1KB")
            .build();

        Split.Output run = result.run(runContext);

        assertThat(run.getUris().size(), is(251));
        assertThat(readAll(run.getUris()), is(String.join("\n", content(12288)) + "\n"));
    }

    private List<String> content(int count) {
        return IntStream
            .range(0, count)
            .mapToObj(value -> StringUtils.leftPad(value + "", 20))
            .collect(Collectors.toList());
    }

    private String readAll(List<URI> uris) throws IOException {
        return uris
            .stream()
            .map(Rethrow.throwFunction(uri -> CharStreams.toString(new InputStreamReader(storageInterface.get(uri)))))
            .collect(Collectors.joining());
    }


    URI storageUpload(int count) throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("unit", "");

        Files.write(tempFile.toPath(), content(count));

        return storageInterface.put(
            new URI("/file/storage/get.yml"),
            new FileInputStream(tempFile)
        );
    }

}