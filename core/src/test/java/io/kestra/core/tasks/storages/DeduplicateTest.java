package io.kestra.core.tasks.storages;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@MicronautTest
class DeduplicateTest {
    private static final List<KeyValue1> TEST_ITEMS_WITH_NULLS = List.of(
        new KeyValue1("k1", "v1"),
        new KeyValue1("k2", "v1"),
        new KeyValue1("k3", "v1"),
        new KeyValue1("k1", "v2"),
        new KeyValue1("k2", "v2"),
        new KeyValue1("k2", null),
        new KeyValue1("k3", "v2"),
        new KeyValue1("k1", "v3")
    );

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void shouldCompactFileGivenKeyExpression() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        Deduplicate task = Deduplicate
            .builder()
            .from(generateKeyValueFile(TEST_ITEMS_WITH_NULLS, runContext).toString())
            .expr(" {{ key }}")
            .build();

        // When
        Deduplicate.Output output = task.run(runContext);

        // Then
        Assertions.assertNotNull(output);
        Assertions.assertNotNull(output.getUri());

        List<KeyValue1> expected = List.of(new KeyValue1("k2", null), new KeyValue1("k3", "v2"), new KeyValue1("k1", "v3"));
        assertSimpleCompactedFile(runContext, output, expected, KeyValue1.class);
    }

    @Test
    void shouldCompactFileGivenKeyExpressionReturningArray() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        List<KeyValue2> values = List.of(
            new KeyValue2("k1", "k1", "v1"),
            new KeyValue2("k2", "k2", "v1"),
            new KeyValue2("k3", "k3", "v1"),
            new KeyValue2("k1", "k1", "v2"),
            new KeyValue2("k2", "k2", null),
            new KeyValue2("k3", "k3", "v2"),
            new KeyValue2("k1", "k1", "v3")
        );

        Deduplicate task = Deduplicate
            .builder()
            .from(generateKeyValueFile(values, runContext).toString())
            .expr(" {{ key }}-{{ v1 }}")
            .build();

        // When
        Deduplicate.Output output = task.run(runContext);

        // Then
        Assertions.assertNotNull(output);
        Assertions.assertNotNull(output.getUri());

        List<KeyValue2> expected = List.of(
            new KeyValue2("k2", "k2", null),
            new KeyValue2("k3", "k3", "v2"),
            new KeyValue2("k1", "k1", "v3")
        );
        assertSimpleCompactedFile(runContext, output, expected, KeyValue2.class);
    }

    private static <T> void assertSimpleCompactedFile(final RunContext runContext,
                                                      final Deduplicate.Output output,
                                                      final List<T> expected,
                                                      final Class<T> type) throws IOException {
        try (InputStream resource = runContext.storage().getFile(output.getUri());
             InputStreamReader inputStreamReader = new InputStreamReader(resource, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            List<T> list = bufferedReader.lines()
                .map(line -> {
                    try {
                        return JacksonMapper.ofIon().readValue(line, type);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }).toList();
            Assertions.assertEquals(expected, list);
        }
    }

    private URI generateKeyValueFile(final List<?> items, RunContext runContext) throws IOException {
        Path path = runContext.tempFile(".ion");
        try (final BufferedWriter writer = Files.newBufferedWriter(path)) {
            items.forEach(object -> {
                try {
                    writer.write(JacksonMapper.ofIon().writeValueAsString(object));
                    writer.newLine();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return runContext.storage().putFile(path.toFile());
    }

    record KeyValue1(String key, Object value) {
    }

    record KeyValue2(String key, Object v1, Object v2) {
    }
}