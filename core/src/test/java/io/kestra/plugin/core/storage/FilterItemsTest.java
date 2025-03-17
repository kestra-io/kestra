package io.kestra.plugin.core.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.junit.annotations.KestraTest;
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

@KestraTest
class FilterItemsTest {
    private static final List<KeyValue> TEST_VALID_ITEMS = List.of(
        new KeyValue("k1", 1),
        new KeyValue("k2", 2),
        new KeyValue("k3", 3),
        new KeyValue("k4", 4)
    );

    private static final List<KeyValue> TEST_INVALID_ITEMS = List.of(
        new KeyValue("k1", 1),
        new KeyValue("k2", "dummy"),
        new KeyValue("k3", 3),
        new KeyValue("k4", 4)
    );

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void shouldFilterGivenValidBooleanExpressionForInclude() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        FilterItems task = FilterItems
            .builder()
            .from(Property.of(generateKeyValueFile(TEST_VALID_ITEMS, runContext).toString()))
            .filterCondition(" {{ value % 2 == 0 }} ")
            .filterType(Property.of(FilterItems.FilterType.INCLUDE))
            .build();

        // When
        FilterItems.Output output = task.run(runContext);

        // Then
        Assertions.assertNotNull(output);
        Assertions.assertNotNull(output.getUri());
        Assertions.assertEquals(2, output.getDroppedItemsTotal());
        Assertions.assertEquals(4, output.getProcessedItemsTotal());
        assertFile(runContext, output, List.of(new KeyValue("k2", 2), new KeyValue("k4", 4)), KeyValue.class);
    }

    @Test
    void shouldFilterGivenValidBooleanExpressionForExclude() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        FilterItems task = FilterItems
            .builder()
            .from(Property.of(generateKeyValueFile(TEST_VALID_ITEMS, runContext).toString()))
            .filterCondition(" {{ value % 2 == 0 }} ")
            .filterType(Property.of(FilterItems.FilterType.EXCLUDE))
            .build();

        // When
        FilterItems.Output output = task.run(runContext);

        // Then
        Assertions.assertNotNull(output);
        Assertions.assertNotNull(output.getUri());
        Assertions.assertEquals(2, output.getDroppedItemsTotal());
        Assertions.assertEquals(4, output.getProcessedItemsTotal());
        assertFile(runContext, output, List.of(new KeyValue("k1", 1), new KeyValue("k3", 3)), KeyValue.class);
    }

    @Test
    void shouldThrowExceptionGivenInvalidRecordsForFail() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        FilterItems task = FilterItems
            .builder()
            .from(Property.of(generateKeyValueFile(TEST_INVALID_ITEMS, runContext).toString()))
            .filterCondition(" {{ value % 2 == 0 }}")
            .filterType(Property.of(FilterItems.FilterType.INCLUDE))
            .errorOrNullBehavior(Property.of(FilterItems.ErrorOrNullBehavior.FAIL))
            .build();

        // When/Then
        Assertions.assertThrows(IllegalVariableEvaluationException.class, () -> task.run(runContext));
    }

    @Test
    void shouldFilterGivenInvalidRecordsForInclude() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        FilterItems task = FilterItems
            .builder()
            .from(Property.of(generateKeyValueFile(TEST_INVALID_ITEMS, runContext).toString()))
            .filterCondition(" {{ value % 2 == 0 }}")
            .filterType(Property.of(FilterItems.FilterType.INCLUDE))
            .errorOrNullBehavior(Property.of(FilterItems.ErrorOrNullBehavior.INCLUDE))
            .build();

        // When
        FilterItems.Output output = task.run(runContext);

        // Then
        Assertions.assertNotNull(output);
        Assertions.assertNotNull(output.getUri());
        Assertions.assertEquals(2, output.getDroppedItemsTotal());
        Assertions.assertEquals(4, output.getProcessedItemsTotal());
        assertFile(runContext, output, List.of(new KeyValue("k2", "dummy"), new KeyValue("k4", 4)), KeyValue.class);
    }

    @Test
    void shouldFilterGivenInvalidRecordsForExclude() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        FilterItems task = FilterItems
            .builder()
            .from(Property.of(generateKeyValueFile(TEST_INVALID_ITEMS, runContext).toString()))
            .filterCondition(" {{ value % 2 == 0 }}")
            .filterType(Property.of(FilterItems.FilterType.INCLUDE))
            .errorOrNullBehavior(Property.of(FilterItems.ErrorOrNullBehavior.EXCLUDE))
            .build();

        // When
        FilterItems.Output output = task.run(runContext);

        // Then
        Assertions.assertNotNull(output);
        Assertions.assertNotNull(output.getUri());
        Assertions.assertEquals(3, output.getDroppedItemsTotal());
        Assertions.assertEquals(4, output.getProcessedItemsTotal());
        assertFile(runContext, output, List.of(new KeyValue("k4", 4)), KeyValue.class);
    }

    @Test
    void shouldFilterWithNotMatchGivenNonBooleanValue() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();

        FilterItems task = FilterItems
            .builder()
            .from(Property.of(generateKeyValueFile(TEST_VALID_ITEMS, runContext).toString()))
            .filterCondition("{{ value }}")
            .filterType(Property.of(FilterItems.FilterType.INCLUDE))
            .errorOrNullBehavior(Property.of(FilterItems.ErrorOrNullBehavior.FAIL))
            .build();

        // When
        FilterItems.Output output = task.run(runContext);

        // Then
        Assertions.assertNotNull(output);
        Assertions.assertNotNull(output.getUri());
        Assertions.assertEquals(0, output.getDroppedItemsTotal());
        Assertions.assertEquals(4, output.getProcessedItemsTotal());
        assertFile(runContext, output, TEST_VALID_ITEMS, KeyValue.class);
    }

    private static <T> void assertFile(final RunContext runContext,
                                       final FilterItems.Output output,
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
        Path path = runContext.workingDir().createTempFile(".ion");
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

    record KeyValue(String key, Object value) { }
}