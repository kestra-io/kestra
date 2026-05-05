package io.kestra.plugin.core.flow;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.core.debug.Return;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class LoopTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void initFromUri_withNoLimit_shouldReturnAllValuesAndCount() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        URI uri = createIonFile(runContext, List.of("a", "b", "c"));
        Loop loop = loop(0);

        // When
        Loop.UriInit result = loop.initFromUri(runContext, uri.toString());

        // Then
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.limit()).isEqualTo(3);
        assertThat(result.values()).containsExactly("a", "b", "c");
        assertThat(result.nextOffset()).isGreaterThan(0);
    }

    @Test
    void initFromUri_withConcurrencyLimit_shouldReturnFirstBatchOnly() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        URI uri = createIonFile(runContext, List.of("v1", "v2", "v3", "v4", "v5"));
        Loop loop = loop(2);

        // When
        Loop.UriInit result = loop.initFromUri(runContext, uri.toString());

        // Then
        assertThat(result.totalCount()).isEqualTo(5);
        assertThat(result.limit()).isEqualTo(2);
        assertThat(result.values()).containsExactly("v1", "v2");
        assertThat(result.nextOffset()).isGreaterThan(0);
    }

    @Test
    void initFromUri_whenLimitExceedsSize_shouldCapAtSize() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        URI uri = createIonFile(runContext, List.of("x", "y"));
        Loop loop = loop(10);

        // When
        Loop.UriInit result = loop.initFromUri(runContext, uri.toString());

        // Then
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.limit()).isEqualTo(2);
        assertThat(result.values()).containsExactly("x", "y");
    }

    @Test
    void initFromUri_withSingleValue_shouldReturnOneEntry() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        URI uri = createIonFile(runContext, List.of("only"));
        Loop loop = loop(1);

        // When
        Loop.UriInit result = loop.initFromUri(runContext, uri.toString());

        // Then
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.limit()).isEqualTo(1);
        assertThat(result.values()).containsExactly("only");
    }

    @Test
    void initFromValues_withStringList_noLimit_shouldReturnAll() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        Loop loop = Loop.builder()
            .id("loop")
            .type(Loop.class.getName())
            .values(List.of("one", "two", "three"))
            .tasks(List.of(Return.builder().id("t").type(Return.class.getName()).format(Property.ofValue("x")).build()))
            .concurrencyLimit(0)
            .build();

        // When
        Loop.ValuesInit result = loop.initFromValues(runContext);

        // Then
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.limit()).isEqualTo(3);
        assertThat(result.values().isLeft()).isTrue();
        assertThat(result.values().getLeft()).containsExactly("one", "two", "three");
    }

    @Test
    void initFromValues_withStringList_withConcurrencyLimit_shouldCapLimit() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        Loop loop = Loop.builder()
            .id("loop")
            .type(Loop.class.getName())
            .values(List.of("a", "b", "c", "d"))
            .tasks(List.of(Return.builder().id("t").type(Return.class.getName()).format(Property.ofValue("x")).build()))
            .concurrencyLimit(2)
            .build();

        // When
        Loop.ValuesInit result = loop.initFromValues(runContext);

        // Then
        assertThat(result.totalCount()).isEqualTo(4);
        assertThat(result.limit()).isEqualTo(2);
        assertThat(result.values().isLeft()).isTrue();
        assertThat(result.values().getLeft()).containsExactly("a", "b", "c", "d");
    }

    @Test
    void initFromValues_whenLimitExceedsSize_shouldCapAtSize() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        Loop loop = Loop.builder()
            .id("loop")
            .type(Loop.class.getName())
            .values(List.of("p", "q"))
            .tasks(List.of(Return.builder().id("t").type(Return.class.getName()).format(Property.ofValue("x")).build()))
            .concurrencyLimit(99)
            .build();

        // When
        Loop.ValuesInit result = loop.initFromValues(runContext);

        // Then
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.limit()).isEqualTo(2);
    }

    @Test
    void initFromValues_withSerialLimit_shouldReturnLimitOne() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        Loop loop = Loop.builder()
            .id("loop")
            .type(Loop.class.getName())
            .values(List.of("x", "y", "z"))
            .tasks(List.of(Return.builder().id("t").type(Return.class.getName()).format(Property.ofValue("x")).build()))
            .concurrencyLimit(1)
            .build();

        // When
        Loop.ValuesInit result = loop.initFromValues(runContext);

        // Then
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.limit()).isEqualTo(1);
    }

    @Test
    void initFromValues_withJsonMap_shouldReturnPairs() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        Loop loop = Loop.builder()
            .id("loop")
            .type(Loop.class.getName())
            .values("{\"key1\": \"val1\", \"key2\": \"val2\"}")
            .tasks(List.of(Return.builder().id("t").type(Return.class.getName()).format(Property.ofValue("x")).build()))
            .concurrencyLimit(0)
            .build();

        // When
        Loop.ValuesInit result = loop.initFromValues(runContext);

        // Then
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.limit()).isEqualTo(2);
        assertThat(result.values().isRight()).isTrue();
        assertThat(result.values().getRight()).containsExactlyInAnyOrder(
            Pair.of("key1", "val1"),
            Pair.of("key2", "val2")
        );
    }

    @Test
    void initFromValues_withMapAndConcurrencyLimit_shouldCapLimit() throws Exception {
        // Given
        RunContext runContext = runContextFactory.of();
        Loop loop = Loop.builder()
            .id("loop")
            .type(Loop.class.getName())
            .values(Map.of("k1", "v1", "k2", "v2", "k3", "v3"))
            .tasks(List.of(Return.builder().id("t").type(Return.class.getName()).format(Property.ofValue("x")).build()))
            .concurrencyLimit(2)
            .build();

        // When
        Loop.ValuesInit result = loop.initFromValues(runContext);

        // Then
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.limit()).isEqualTo(2);
        assertThat(result.values().isRight()).isTrue();
    }

    private Loop loop(int concurrencyLimit) {
        return Loop.builder()
            .id("loop")
            .type(Loop.class.getName())
            .values(List.of("placeholder"))
            .tasks(List.of(Return.builder().id("t").type(Return.class.getName()).format(Property.ofValue("x")).build()))
            .concurrencyLimit(concurrencyLimit)
            .build();
    }

    /** Creates an ION file from a list of strings, stores it, and returns its kestra:// URI. */
    private URI createIonFile(RunContext runContext, List<String> values) throws IOException {
        Path path = runContext.workingDir().createTempFile(".ion");
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (String value : values) {
                writer.write(JacksonMapper.ofIon().writeValueAsString(value));
                writer.newLine();
            }
        }
        return runContext.storage().putFile(path.toFile());
    }
}
