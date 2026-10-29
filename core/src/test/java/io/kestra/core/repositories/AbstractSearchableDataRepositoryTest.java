package io.kestra.core.repositories;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.searchable.SearchableData;
import io.kestra.core.searchable.SearchableDataRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest(transactional = false)
public abstract class AbstractSearchableDataRepositoryTest {
    @Inject
    protected SearchableDataRepositoryInterface searchableDataRepository;

    private Execution execution(String tenant) {
        return Execution.builder()
            .id(IdUtils.create())
            .tenantId(tenant)
            .namespace("io.kestra.test")
            .flowId("flow")
            .build();
    }

    @Test
    void shouldSaveSearchableOutput() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        var output = new SearchableData(tenant, execution.getId(), "output", "value");

        searchableDataRepository.save(output);

        assertThat(searchableDataRepository.findById(tenant, execution.getId(), "output")).contains(output);
    }

    @Test
    void shouldSaveSearchableOutputWithNullValue() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        var output = new SearchableData(tenant, execution.getId(), "output", null);

        searchableDataRepository.save(output);

        assertThat(searchableDataRepository.findById(tenant, execution.getId(), "output")).contains(output);
    }

    @Test
    void shouldSaveLongSearchableOutputValue() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        var output = new SearchableData(tenant, execution.getId(), "output", "a".repeat(2_048));

        searchableDataRepository.save(output);

        assertThat(searchableDataRepository.findById(tenant, execution.getId(), "output")).contains(output);
    }

    @Test
    void shouldUpdateExistingSearchableOutput() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        searchableDataRepository.save(new SearchableData(tenant, execution.getId(), "output", "initial"));
        var updated = new SearchableData(tenant, execution.getId(), "output", "updated");

        searchableDataRepository.save(updated);

        assertThat(searchableDataRepository.findById(tenant, execution.getId(), "output")).contains(updated);
        assertThat(searchableDataRepository.purge(List.of(execution))).isEqualTo(1);
    }

    @Test
    void shouldFindSearchableOutputById() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        var first = new SearchableData(tenant, execution.getId(), "first", "value1");
        var second = new SearchableData(tenant, execution.getId(), "second", "value2");
        searchableDataRepository.save(first);
        searchableDataRepository.save(second);

        assertThat(searchableDataRepository.findById(tenant, execution.getId(), "first")).contains(first);
        assertThat(searchableDataRepository.findById(tenant, execution.getId(), "second")).contains(second);
    }

    @Test
    void shouldReturnEmptyWhenSearchableOutputIsNotFound() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        assertThat(searchableDataRepository.findById(tenant, IdUtils.create(), "output")).isEmpty();
    }

    @Test
    void shouldFindSearchableOutputOfTheGivenTenantAndExecutionOnly() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        searchableDataRepository.save(new SearchableData(tenant, execution.getId(), "output", "value"));

        assertThat(searchableDataRepository.findById(TestsUtils.randomTenant(this.getClass().getSimpleName()), execution.getId(), "output")).isEmpty();
        assertThat(searchableDataRepository.findById(tenant, execution(tenant).getId(), "output")).isEmpty();
        assertThat(searchableDataRepository.findById(tenant, execution.getId(), "other")).isEmpty();
    }

    @Test
    void shouldPurgeAllSearchableOutputsOfTheGivenExecutions() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution1 = execution(tenant);
        Execution execution2 = execution(tenant);
        Execution untouched = execution(tenant);
        searchableDataRepository.save(new SearchableData(tenant, execution1.getId(), "first", "value"));
        searchableDataRepository.save(new SearchableData(tenant, execution1.getId(), "second", "value"));
        searchableDataRepository.save(new SearchableData(tenant, execution2.getId(), "first", "value"));
        searchableDataRepository.save(new SearchableData(tenant, untouched.getId(), "first", "value"));

        int purged = searchableDataRepository.purge(List.of(execution1, execution2));

        assertThat(purged).isEqualTo(3);
        assertThat(searchableDataRepository.findById(tenant, execution1.getId(), "first")).isEmpty();
        assertThat(searchableDataRepository.findById(tenant, execution1.getId(), "second")).isEmpty();
        assertThat(searchableDataRepository.findById(tenant, execution2.getId(), "first")).isEmpty();
        assertThat(searchableDataRepository.findById(tenant, untouched.getId(), "first")).isPresent();
    }

    @Test
    void shouldPurgeNothingWhenExecutionHasNoSearchableOutput() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        assertThat(searchableDataRepository.purge(List.of(execution(tenant)))).isZero();
    }

    @Test
    void shouldFindExecutionIdsByOutputValue() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution exact = execution(tenant);
        Execution otherValue = execution(tenant);
        Execution otherOutputId = execution(tenant);
        searchableDataRepository.save(new SearchableData(tenant, exact.getId(), "orderIds", "ORD-8841"));
        searchableDataRepository.save(new SearchableData(tenant, otherValue.getId(), "orderIds", "ORD-8841-bis"));
        searchableDataRepository.save(new SearchableData(tenant, otherOutputId.getId(), "other", "ORD-8841"));

        assertThat(searchableDataRepository.findExecutionIds(tenant, "orderIds", Op.EQUALS, "ORD-8841", 10))
            .containsExactly(exact.getId());
    }

    @Test
    void shouldMatchOutputValueCaseSensitively() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        searchableDataRepository.save(new SearchableData(tenant, execution.getId(), "output", "Value_1"));

        assertThat(searchableDataRepository.findExecutionIds(tenant, "output", Op.EQUALS, "value_1", 10)).isEmpty();
        assertThat(searchableDataRepository.findExecutionIds(tenant, "output", Op.EQUALS, "Value_1", 10)).containsExactly(execution.getId());
    }

    @Test
    void shouldFindExecutionIdsOfTheGivenTenantOnly() {
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant1);
        searchableDataRepository.save(new SearchableData(tenant1, execution.getId(), "output", "value"));

        assertThat(searchableDataRepository.findExecutionIds(tenant2, "output", Op.EQUALS, "value", 10)).isEmpty();
    }

    @Test
    void shouldLimitFoundExecutionIds() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        for (int i = 0; i < 3; i++) {
            searchableDataRepository.save(new SearchableData(tenant, execution(tenant).getId(), "output", "value"));
        }

        assertThat(searchableDataRepository.findExecutionIds(tenant, "output", Op.EQUALS, "value", 2)).hasSize(2);
    }

    @Test
    void shouldRejectUnsupportedOperationWhenFindingExecutionIds() {
        assertThatThrownBy(() -> searchableDataRepository.findExecutionIds("tenant", "output", Op.REGEX, "value", 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldFindOnlyExecutionsHavingTheOutputWithAnotherValueForNegativeOperations() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution matching = execution(tenant);
        Execution other = execution(tenant);
        Execution another = execution(tenant);
        Execution otherOutputId = execution(tenant);
        searchableDataRepository.save(new SearchableData(tenant, matching.getId(), "orderIds", "ORD-1"));
        searchableDataRepository.save(new SearchableData(tenant, other.getId(), "orderIds", "ORD-2"));
        searchableDataRepository.save(new SearchableData(tenant, another.getId(), "orderIds", "ORD-3"));
        searchableDataRepository.save(new SearchableData(tenant, otherOutputId.getId(), "other", "ORD-2"));

        assertThat(searchableDataRepository.findExecutionIds(tenant, "orderIds", Op.NOT_EQUALS, "ORD-1", 10))
            .containsExactlyInAnyOrder(other.getId(), another.getId());
        assertThat(searchableDataRepository.findExecutionIds(tenant, "orderIds", Op.NOT_IN, List.of("ORD-1", "ORD-2"), 10))
            .containsExactly(another.getId());
    }

    @Test
    void shouldFindExecutionIdsMatchingAnyOfTheValues() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution first = execution(tenant);
        Execution second = execution(tenant);
        Execution third = execution(tenant);
        searchableDataRepository.save(new SearchableData(tenant, first.getId(), "orderIds", "ORD-1"));
        searchableDataRepository.save(new SearchableData(tenant, second.getId(), "orderIds", "ORD-2"));
        searchableDataRepository.save(new SearchableData(tenant, third.getId(), "orderIds", "ORD-3"));

        assertThat(searchableDataRepository.findExecutionIds(tenant, "orderIds", Op.IN, List.of("ORD-1", "ORD-3"), 10))
            .containsExactlyInAnyOrder(first.getId(), third.getId());
    }

    @Test
    void shouldRejectScalarValueForListOperations() {
        assertThatThrownBy(() -> searchableDataRepository.findExecutionIds("tenant", "output", Op.IN, "a", 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldListSearchableOutputsOfAnExecutionPagedAndOrderedByOutputId() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        Execution other = execution(tenant);
        for (String outputId : List.of("c", "a", "d", "b")) {
            searchableDataRepository.save(new SearchableData(tenant, execution.getId(), outputId, "value-" + outputId));
        }
        searchableDataRepository.save(new SearchableData(tenant, other.getId(), "other", "value"));

        var first = searchableDataRepository.findByExecutionId(tenant, execution.getId(), Pageable.from(1, 3));
        var second = searchableDataRepository.findByExecutionId(tenant, execution.getId(), Pageable.from(2, 3));

        assertThat(first.getTotal()).isEqualTo(4);
        assertThat(first).extracting(SearchableData::dataId).containsExactly("a", "b", "c");
        assertThat(first.getFirst()).isEqualTo(new SearchableData(tenant, execution.getId(), "a", "value-a"));
        assertThat(second).extracting(SearchableData::dataId).containsExactly("d");
    }

    @Test
    void shouldListNoSearchableOutputForAnotherTenantOrAnUnknownExecution() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        searchableDataRepository.save(new SearchableData(tenant, execution.getId(), "output", "value"));

        var anotherTenant = searchableDataRepository.findByExecutionId(TestsUtils.randomTenant(this.getClass().getSimpleName()), execution.getId(), Pageable.from(1, 10));
        var unknown = searchableDataRepository.findByExecutionId(tenant, IdUtils.create(), Pageable.from(1, 10));

        assertThat(anotherTenant).isEmpty();
        assertThat(anotherTenant.getTotal()).isZero();
        assertThat(unknown).isEmpty();
    }

    private int saveItems(String tenant, String executionId, String outputId, List<String> values) {
        return searchableDataRepository.saveBatch(IntStream.range(0, values.size())
            .mapToObj(index -> new SearchableData(tenant, executionId, outputId, index, values.get(index)))
            .toList());
    }

    @Test
    void shouldSaveEachItemOfAnArrayOutputKeepingTheirPosition() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        searchableDataRepository.save(new SearchableData(tenant, execution.getId(), "other", "kept"));

        int saved = saveItems(tenant, execution.getId(), "orderIds", List.of("a", "b", "c"));

        assertThat(saved).isEqualTo(3);
        assertThat(searchableDataRepository.findByExecutionId(tenant, execution.getId(), Pageable.from(1, 10))).containsExactly(
            new SearchableData(tenant, execution.getId(), "orderIds", 0, "a"),
            new SearchableData(tenant, execution.getId(), "orderIds", 1, "b"),
            new SearchableData(tenant, execution.getId(), "orderIds", 2, "c"),
            new SearchableData(tenant, execution.getId(), "other", 0, "kept")
        );
        assertThat(searchableDataRepository.findById(tenant, execution.getId(), "orderIds")).contains(new SearchableData(tenant, execution.getId(), "orderIds", 0, "a"));
    }

    @Test
    void shouldOnlyUpdateTheValueWhenAnItemIsSavedAgain() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        saveItems(tenant, execution.getId(), "orderIds", List.of("a", "b"));

        saveItems(tenant, execution.getId(), "orderIds", List.of("x", "y"));

        assertThat(searchableDataRepository.findByExecutionId(tenant, execution.getId(), Pageable.from(1, 10))).containsExactly(
            new SearchableData(tenant, execution.getId(), "orderIds", 0, "x"),
            new SearchableData(tenant, execution.getId(), "orderIds", 1, "y")
        );
    }

    @Test
    void shouldSaveNothingForAnEmptyBatch() {
        assertThat(searchableDataRepository.saveBatch(List.of())).isZero();
    }

    @Test
    void shouldKeepTheSameExecutionIdIndependentAcrossTenants() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String otherTenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        saveItems(tenant, execution.getId(), "orderIds", List.of("a"));
        saveItems(otherTenant, execution.getId(), "orderIds", List.of("other-tenant"));

        assertThat(searchableDataRepository.findById(tenant, execution.getId(), "orderIds")).map(SearchableData::value).contains("a");
        assertThat(searchableDataRepository.findById(otherTenant, execution.getId(), "orderIds")).map(SearchableData::value).contains("other-tenant");
    }

    @Test
    void shouldFindAnExecutionOnceWhateverTheNumberOfMatchingItems() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        Execution other = execution(tenant);
        saveItems(tenant, execution.getId(), "orderIds", List.of("ORD-1", "ORD-2", "ORD-1"));
        saveItems(tenant, other.getId(), "orderIds", List.of("ORD-2"));

        assertThat(searchableDataRepository.findExecutionIds(tenant, "orderIds", Op.EQUALS, "ORD-1", 10)).containsExactly(execution.getId());
        assertThat(searchableDataRepository.findExecutionIds(tenant, "orderIds", Op.IN, List.of("ORD-1", "ORD-2"), 10))
            .containsExactlyInAnyOrder(execution.getId(), other.getId());
    }

    @Test
    void shouldMatchNegativeOperationsOnAnyItemDifferingForArrayOutputs() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution mixed = execution(tenant);
        Execution onlyExcluded = execution(tenant);
        saveItems(tenant, mixed.getId(), "orderIds", List.of("a", "b"));
        saveItems(tenant, onlyExcluded.getId(), "orderIds", List.of("a"));

        assertThat(searchableDataRepository.findExecutionIds(tenant, "orderIds", Op.NOT_EQUALS, "a", 10)).containsExactly(mixed.getId());
        assertThat(searchableDataRepository.findExecutionIds(tenant, "orderIds", Op.NOT_IN, List.of("a"), 10)).containsExactly(mixed.getId());
    }

    @Test
    void shouldStoreValuesWithSpecialCharactersAsIs() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        List<String> values = List.of("it's \"quoted\"", "back\\slash \\' end", "question ? mark", "unicode é 日本語 😀", "semi; -- comment");

        saveItems(tenant, execution.getId(), "output", values);

        assertThat(searchableDataRepository.findByExecutionId(tenant, execution.getId(), Pageable.from(1, 10)))
            .extracting(SearchableData::value)
            .containsExactlyElementsOf(values);
        assertThat(searchableDataRepository.findExecutionIds(tenant, "output", Op.EQUALS, "it's \"quoted\"", 10)).containsExactly(execution.getId());
    }

    @Test
    void shouldStoreAndSearchAValueOfTheMaximumSize() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        String value = new java.util.Random(42).ints(2_048, 'a', 'z' + 1).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();

        searchableDataRepository.save(new SearchableData(tenant, execution.getId(), "output", value));

        assertThat(searchableDataRepository.findById(tenant, execution.getId(), "output").orElseThrow().value()).isEqualTo(value);
        assertThat(searchableDataRepository.findExecutionIds(tenant, "output", Op.EQUALS, value, 10)).containsExactly(execution.getId());
    }

    @Test
    void shouldFindAllTheDataOfAnExecutionWhenUnpaged() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant);
        searchableDataRepository.saveBatch(List.of(
            new SearchableData(tenant, execution.getId(), "b", 0, "2"),
            new SearchableData(tenant, execution.getId(), "a", 1, "1"),
            new SearchableData(tenant, execution.getId(), "a", 0, "0")
        ));

        var all = searchableDataRepository.findByExecutionId(tenant, execution.getId(), Pageable.UNPAGED);

        assertThat(all).extracting(SearchableData::dataId, SearchableData::index).containsExactly(tuple("a", 0), tuple("a", 1), tuple("b", 0));
    }

    @Test
    void shouldSearchLongValuesWithSupplementaryCharactersAlongsideShortOnes() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution longValueExecution = execution(tenant);
        Execution shortValueExecution = execution(tenant);
        String longValue = "\uD83D\uDE00".repeat(300);
        saveItems(tenant, longValueExecution.getId(), "output", List.of(longValue));
        saveItems(tenant, shortValueExecution.getId(), "output", List.of("short"));

        assertThat(searchableDataRepository.findExecutionIds(tenant, "output", Op.EQUALS, longValue, 10)).containsExactly(longValueExecution.getId());
        assertThat(searchableDataRepository.findExecutionIds(tenant, "output", Op.IN, List.of(longValue, "short"), 10))
            .containsExactlyInAnyOrder(longValueExecution.getId(), shortValueExecution.getId());
        assertThat(searchableDataRepository.findExecutionIds(tenant, "output", Op.EQUALS, longValue + "x", 10)).isEmpty();
    }
}
