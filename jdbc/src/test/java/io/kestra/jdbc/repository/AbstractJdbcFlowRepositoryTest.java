package io.kestra.jdbc.repository;

import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.plugin.core.debug.Return;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import static io.kestra.jdbc.repository.AbstractJdbcRepository.field;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public abstract class AbstractJdbcFlowRepositoryTest extends io.kestra.core.repositories.AbstractFlowRepositoryTest {
    @Inject
    protected AbstractJdbcFlowRepository flowRepository;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    protected JooqDSLContextWrapper dslContextWrapper;

    @Test
    public void findSourceCode() {
        List<SearchResult<Flow>> search = flowRepository.findSourceCode(Pageable.from(1, 10, Sort.UNSORTED), "io.kestra.plugin.core.condition.MultipleCondition", null, null);

        assertThat((long) search.size(), is(2L));

        SearchResult<Flow> flow = search
            .stream()
            .filter(flowSearchResult -> flowSearchResult.getModel()
                .getId()
                .equals("trigger-multiplecondition-listener"))
            .findFirst()
            .orElseThrow();
        assertThat(flow.getFragments().getFirst(), containsString("condition.MultipleCondition[/mark]"));
    }

    @Test
    public void invalidFlow() {
        dslContextWrapper.transaction(configuration -> {
            DSLContext context = DSL.using(configuration);

            context.insertInto(flowRepository.jdbcRepository.getTable())
                .set(field("key"), "io.kestra.unittest_invalid")
                .set(field("source_code"), "")
                .set(field("value"), JacksonMapper.ofJson().writeValueAsString(Map.of(
                    "id", "invalid",
                    "namespace", "io.kestra.unittest",
                    "revision", 1,
                    "tasks", List.of(Map.of(
                        "id", "invalid",
                        "type", "io.kestra.plugin.core.log.Log",
                        "level", "invalid"
                    )),
                    "deleted", false
                )))
                .execute();
        });

        Optional<Flow> flow = flowRepository.findById(null, "io.kestra.unittest", "invalid");

        try {
            assertThat(flow.isPresent(), is(true));
            assertThat(flow.get(), instanceOf(FlowWithException.class));
            assertThat(((FlowWithException) flow.get()).getException(), containsString("Cannot deserialize value of type `org.slf4j.event.Level`"));
        } finally {
            flow.ifPresent(value -> flowRepository.delete(value));
        }
    }

    @BeforeAll
    protected void setup() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @Test
    void shouldCountForNullTenant() {
        FlowWithSource toDelete = null;
        try {
            // Given
            Flow flow = createTestFlowForNamespace("io.kestra.unittest");
            toDelete = flowRepository.create(flow, "", flow);
            // When
            int count = flowRepository.count(null);

            // Then
            Assertions.assertTrue(count > 0);
        } finally {
            Optional.ofNullable(toDelete).ifPresent(flow -> {
                flowRepository.delete(flow.toFlow());
            });
        }
    }

    @Test
    void shouldCountForNullTenantGivenNamespace() {
        List<FlowWithSource> toDelete = new ArrayList<>();
        try {
            // Given
            toDelete.add(flowRepository.create(createTestFlowForNamespace("io.kestra.unittest"), "", createTestFlowForNamespace("io.kestra.unittest")));
            toDelete.add(flowRepository.create(createTestFlowForNamespace("io.kestra.unittest.shouldcountbynamespacefornulltenant"), "", createTestFlowForNamespace("io.kestra.unittest.shouldcountbynamespacefornulltenant")));

            // When
            int count = flowRepository.countForNamespace(null, "io.kestra.unittest.shouldcountbynamespacefornulltenant");

            // Then
            Assertions.assertEquals(1, count);
        } finally {
            for (FlowWithSource flow : toDelete) {
                flowRepository.delete(flow.toFlow());
            }
        }
    }

    private static Flow createTestFlowForNamespace(String namespace) {
        return Flow.builder()
            .id(IdUtils.create())
            .namespace(namespace)
            .tasks(List.of(Return.builder()
                .id(IdUtils.create())
                .type(Return.class.getName())
                .build()
            ))
            .build();
    }
}