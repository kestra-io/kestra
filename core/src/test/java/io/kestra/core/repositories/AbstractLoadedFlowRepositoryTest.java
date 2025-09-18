package io.kestra.core.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.Helpers;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@KestraTest
public abstract class AbstractLoadedFlowRepositoryTest {

    @Inject
    protected FlowRepositoryInterface flowRepository;

    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;

    protected static final String TENANT = TestsUtils.randomTenant(AbstractLoadedFlowRepositoryTest.class.getSimpleName());
    private static final AtomicBoolean IS_INIT = new AtomicBoolean();

    @BeforeEach
    protected synchronized void init() throws IOException, URISyntaxException {
        initFlows(repositoryLoader);
    }

    protected static synchronized void initFlows(LocalFlowRepositoryLoader repo) throws IOException, URISyntaxException {
        if (!IS_INIT.get()){
            TestsUtils.loads(TENANT, repo);
            IS_INIT.set(true);
        }
    }

    @Test
    void findAll() {
        List<Flow> save = flowRepository.findAll(TENANT);

        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT);
    }

    @Test
    void findAllWithSource() {
        List<FlowWithSource> save = flowRepository.findAllWithSource(TENANT);

        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT);
    }

    @Test
    void findAllForAllTenants() {
        List<Flow> save = flowRepository.findAllForAllTenants();

        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT);
    }

    @Test
    void findAllWithSourceForAllTenants() {
        List<FlowWithSource> save = flowRepository.findAllWithSourceForAllTenants();

        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT);
    }

    @Test
    void findByNamespace() {
        List<Flow> save = flowRepository.findByNamespace(TENANT, "io.kestra.tests");
        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT - 24);

        save = flowRepository.findByNamespace(TENANT, "io.kestra.tests2");
        assertThat((long) save.size()).isEqualTo(1L);

        save = flowRepository.findByNamespace(TENANT, "io.kestra.tests.minimal.bis");
        assertThat((long) save.size()).isEqualTo(1L);
    }

    @Test
    void findByNamespacePrefix() {
        List<Flow> save = flowRepository.findByNamespacePrefix(TENANT, "io.kestra.tests");
        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT - 1);

        save = flowRepository.findByNamespace(TENANT, "io.kestra.tests2");
        assertThat((long) save.size()).isEqualTo(1L);

        save = flowRepository.findByNamespace(TENANT, "io.kestra.tests.minimal.bis");
        assertThat((long) save.size()).isEqualTo(1L);
    }

    @Test
    void findByNamespacePrefixWithSource() {
        List<FlowWithSource> save = flowRepository.findByNamespacePrefixWithSource(TENANT, "io.kestra.tests");
        assertThat((long) save.size()).isEqualTo(Helpers.FLOWS_COUNT - 1);
    }

    @Test
    void find_paginationPartial() {
        assertThat(flowRepository.find(Pageable.from(1, (int) Helpers.FLOWS_COUNT - 1, Sort.UNSORTED), TENANT, null)
            .size())
            .describedAs("When paginating at MAX-1, it should return MAX-1")
            .isEqualTo(Helpers.FLOWS_COUNT - 1);

        assertThat(flowRepository.findWithSource(Pageable.from(1, (int) Helpers.FLOWS_COUNT - 1, Sort.UNSORTED), TENANT, null)
            .size())
            .describedAs("When paginating at MAX-1, it should return MAX-1")
            .isEqualTo(Helpers.FLOWS_COUNT - 1);
    }

    @Test
    void find_paginationGreaterThanExisting() {
        assertThat(flowRepository.find(Pageable.from(1, (int) Helpers.FLOWS_COUNT + 1, Sort.UNSORTED), TENANT, null)
            .size())
            .describedAs("When paginating requesting a larger amount than existing, it should return existing MAX")
            .isEqualTo(Helpers.FLOWS_COUNT);
        assertThat(flowRepository.findWithSource(Pageable.from(1, (int) Helpers.FLOWS_COUNT + 1, Sort.UNSORTED), TENANT, null)
            .size())
            .describedAs("When paginating requesting a larger amount than existing, it should return existing MAX")
            .isEqualTo(Helpers.FLOWS_COUNT);
    }

    @Test
    void find_prefixMatchingAllNamespaces() {
        assertThat(flowRepository.find(
            Pageable.UNPAGED,
            TENANT,
            List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.STARTS_WITH).value("io.kestra.tests").build()
            )
        ).size())
            .describedAs("When filtering on NAMESPACE START_WITH a pattern that match all, it should return all")
            .isEqualTo(Helpers.FLOWS_COUNT);

        assertThat(flowRepository.findWithSource(
            Pageable.UNPAGED,
            TENANT,
            List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.STARTS_WITH).value("io.kestra.tests").build()
            )
        ).size())
            .describedAs("When filtering on NAMESPACE START_WITH a pattern that match all, it should return all")
            .isEqualTo(Helpers.FLOWS_COUNT);
    }

    @Test
    void find_aSpecifiedNamespace() {
        assertThat(flowRepository.find(
            Pageable.UNPAGED,
            TENANT,
            List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests2").build()
            )
        ).size()).isEqualTo(1L);

        assertThat(flowRepository.findWithSource(
            Pageable.UNPAGED,
            TENANT,
            List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests2").build()
            )
        ).size()).isEqualTo(1L);
    }

    @Test
    void find_aSpecificSubNamespace() {
        assertThat(flowRepository.find(
            Pageable.UNPAGED,
            TENANT,
            List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests.minimal.bis").build()
            )
        ).size())
            .isEqualTo(1L);

        assertThat(flowRepository.findWithSource(
            Pageable.UNPAGED,
            TENANT,
            List.of(
                QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests.minimal.bis").build()
            )
        ).size())
            .isEqualTo(1L);
    }

    @Test
    void find_aSpecificLabel() {
        assertThat(
            flowRepository.find(Pageable.UNPAGED, TENANT,
                List.of(
                    QueryFilter.builder().field(QueryFilter.Field.LABELS).operation(QueryFilter.Op.EQUALS).value(
                        Map.of("country", "FR")).build()
                )
            ).size())
            .isEqualTo(1);

        assertThat(
            flowRepository.findWithSource(Pageable.UNPAGED, TENANT,
                List.of(
                    QueryFilter.builder().field(QueryFilter.Field.LABELS).operation(QueryFilter.Op.EQUALS).value(Map.of("country", "FR")).build()
                )
            ).size())
            .isEqualTo(1);
    }

    @Test
    void find_aSpecificFlowByNamespaceAndLabel() {
        assertThat(
            flowRepository.find(Pageable.UNPAGED, TENANT,
                List.of(
                    QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests").build(),
                    QueryFilter.builder().field(QueryFilter.Field.LABELS).operation(QueryFilter.Op.EQUALS).value(Map.of("key2", "value2")).build()
                )
            ).size())
            .isEqualTo(1);

        assertThat(
            flowRepository.findWithSource(Pageable.UNPAGED, TENANT,
                List.of(
                    QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests").build(),
                    QueryFilter.builder().field(QueryFilter.Field.LABELS).operation(QueryFilter.Op.EQUALS).value(Map.of("key2", "value2")).build()
                )
            ).size())
            .isEqualTo(1);
    }

    @Test
    void find_noResult_forAnUnknownNamespace() {
        assertThat(
            flowRepository.find(Pageable.UNPAGED, TENANT,
                List.of(
                    QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests").build(),
                    QueryFilter.builder().field(QueryFilter.Field.LABELS).operation(QueryFilter.Op.EQUALS).value(Map.of("key1", "value2")).build()
                )
            ).size())
            .isEqualTo(0);

        assertThat(
            flowRepository.findWithSource(Pageable.UNPAGED, TENANT,
                List.of(
                    QueryFilter.builder().field(QueryFilter.Field.NAMESPACE).operation(QueryFilter.Op.EQUALS).value("io.kestra.tests").build(),
                    QueryFilter.builder().field(QueryFilter.Field.LABELS).operation(QueryFilter.Op.EQUALS).value(Map.of("key1", "value2")).build()
                )
            ).size())
            .isEqualTo(0);
    }

    @Test
    protected void findSpecialChars() {
        ArrayListTotal<SearchResult<Flow>> save = flowRepository.findSourceCode(Pageable.unpaged(), "https://api.chucknorris.io", TENANT, null);
        assertThat((long) save.size()).isEqualTo(2L);
    }


    @Test
    void findDistinctNamespace() {
        List<String> distinctNamespace = flowRepository.findDistinctNamespace(TENANT);
        assertThat((long) distinctNamespace.size()).isEqualTo(9L);
    }

    @Test
    void shouldReturnForGivenQueryWildCardFilters() {
        List<QueryFilter> filters = List.of(
            QueryFilter.builder().field(QueryFilter.Field.QUERY).operation(QueryFilter.Op.EQUALS).value("*").build()
        );
        ArrayListTotal<Flow> flows = flowRepository.find(Pageable.from(1, 10), TENANT, filters);
        assertThat(flows.size()).isEqualTo(10);
        assertThat(flows.getTotal()).isEqualTo(Helpers.FLOWS_COUNT);
    }

}
