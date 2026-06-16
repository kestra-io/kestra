package io.kestra.plugin.core.storage;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.google.common.io.CharStreams;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.Rethrow;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class SplitTest {
    @Inject
    TestRunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void partition() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUpload(1000);

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .partitions(Property.ofValue(8))
            .build();

        Split.Output run = result.run(runContext);

        assertThat(run.getUris().size()).isEqualTo(8);
        assertThat(run.getUris().getFirst().getPath()).endsWith(".yml");
        assertThat(StringUtils.countMatches(readAll(run.getUris()), "\n")).isEqualTo(1000);
    }

    @Test
    void rows() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUpload(1000);

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .rows(Property.ofValue(10))
            .build();

        Split.Output run = result.run(runContext);

        assertThat(run.getUris().size()).isEqualTo(100);
        assertThat(readAll(run.getUris())).isEqualTo(String.join("\n", content(1000)) + "\n");
    }

    @Test
    void bytes() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUpload(12288);

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .bytes(Property.ofValue("1KB"))
            .build();

        Split.Output run = result.run(runContext);

        assertThat(run.getUris().size()).isEqualTo(251);
        assertThat(readAll(run.getUris())).isEqualTo(String.join("\n", content(12288)) + "\n");
    }

    @Test
    void regexPattern() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUploadWithRegexContent();

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .regexPattern(Property.ofValue("\\[(\\w+)\\]"))
            .build();

        Split.Output run = result.run(runContext);
        assertThat(run.getUris().size()).isEqualTo(3);

        String allContent = readAll(run.getUris());
        assertThat(allContent).contains("[ERROR] Error message 1");
        assertThat(allContent).contains("[WARN] Warning message 1");
        assertThat(allContent).contains("[INFO] Info message 1");
        assertThat(allContent).contains("[ERROR] Error message 2");
    }

    @Test
    void partitionIon() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUploadIon(ionContent(1000));

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .partitions(Property.ofValue(8))
            .build();

        Split.Output run = result.run(runContext);

        assertThat(run.getUris().size()).isEqualTo(8);
        assertThat(run.getUris().getFirst().getPath()).endsWith(".ion");
        assertThat(readAllIon(run.getUris())).hasSize(1000);
    }

    @Test
    void rowsIon() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUploadIon(ionContent(1000));

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .rows(Property.ofValue(10))
            .build();

        Split.Output run = result.run(runContext);

        assertThat(run.getUris().size()).isEqualTo(100);

        List<Object> records = readAllIon(run.getUris());
        assertThat(records).hasSize(1000);
        // order is preserved across the split
        List<Integer> ids = records.stream().map(record -> ((Number) ((Map<?, ?>) record).get("id")).intValue()).toList();
        assertThat(ids).isEqualTo(IntStream.range(0, 1000).boxed().toList());
    }

    @Test
    void bytesIon() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUploadIon(ionContent(1000));

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .bytes(Property.ofValue("1KB"))
            .build();

        Split.Output run = result.run(runContext);

        assertThat(run.getUris().size()).isGreaterThan(1);
        assertThat(readAllIon(run.getUris())).hasSize(1000);
    }

    @Test
    void regexPatternIon() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUploadIon(List.of(
            Map.of("id", 1, "level", "ERROR"),
            Map.of("id", 2, "level", "WARN"),
            Map.of("id", 3, "level", "INFO"),
            Map.of("id", 4, "level", "ERROR"),
            Map.of("id", 5, "level", "WARN"),
            Map.of("id", 6, "level", "INFO"),
            Map.of("id", 7),
            Map.of("id", 8, "level", "ERROR")
        ));

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .regexPattern(Property.ofValue("level:\"(\\w+)\""))
            .build();

        Split.Output run = result.run(runContext);
        // one file per distinct level (ERROR, WARN, INFO); the record without a level is dropped
        assertThat(run.getUris().size()).isEqualTo(3);

        List<Object> records = readAllIon(run.getUris());
        assertThat(records).hasSize(7);
        List<String> levels = records.stream().map(record -> (String) ((Map<?, ?>) record).get("level")).toList();
        assertThat(levels).containsOnly("ERROR", "WARN", "INFO");
    }

    private List<String> content(int count) {
        return IntStream
            .range(0, count)
            .mapToObj(value -> StringUtils.leftPad(value + "", 20))
            .toList();
    }

    private String readAll(List<URI> uris) throws IOException {
        return uris
            .stream()
            .map(Rethrow.throwFunction(uri -> CharStreams.toString(new InputStreamReader(storageInterface.get(MAIN_TENANT, null, uri)))))
            .collect(Collectors.joining());
    }

    URI storageUpload(int count) throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("unit", "");

        Files.write(tempFile.toPath(), content(count));

        return storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/file/storage/%s/get.yml".formatted(IdUtils.create())),
            new FileInputStream(tempFile)
        );
    }

    URI storageUploadWithRegexContent() throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("unit", "");

        List<String> regexContent = List.of(
            "[ERROR] Error message 1",
            "[WARN] Warning message 1",
            "[INFO] Info message 1",
            "[ERROR] Error message 2",
            "[WARN] Warning message 2",
            "[INFO] Info message 2",
            "Line without pattern",
            "[ERROR] Error message 3"
        );

        Files.write(tempFile.toPath(), regexContent);

        return storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/file/storage/%s/get.yml".formatted(IdUtils.create())),
            new FileInputStream(tempFile)
        );
    }

    private List<Map<String, Object>> ionContent(int count) {
        return IntStream
            .range(0, count)
            .mapToObj(value -> Map.<String, Object>of("id", value))
            .toList();
    }

    URI storageUploadIon(List<Map<String, Object>> records) throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("unit", ".ion");

        try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(tempFile));
             SequenceWriter writer = FileSerde.createBinarySequenceWriter(outputStream, new TypeReference<Object>() {})) {
            for (Map<String, Object> record : records) {
                writer.write(record);
            }
        }

        return storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/file/storage/%s/get.ion".formatted(IdUtils.create())),
            new FileInputStream(tempFile)
        );
    }

    private List<Object> readAllIon(List<URI> uris) throws IOException {
        List<Object> records = new ArrayList<>();
        for (URI uri : uris) {
            try (InputStream inputStream = storageInterface.get(MAIN_TENANT, null, uri)) {
                FileSerde.read(inputStream, records::add);
            }
        }
        return records;
    }

}