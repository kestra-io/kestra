package io.kestra.jdbc.repository;

import io.kestra.core.Helpers;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
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
    protected void find() {
        List<Flow> save = flowRepository.find(Pageable.from(1, 100, Sort.of(Sort.Order.asc("id"))), null, null, null);
        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT));

        save = flowRepository.find(Pageable.from(1, 10, Sort.UNSORTED), "trigger-multiplecondition", null, null);
        assertThat((long) save.size(), is(6L));

        save = flowRepository.find(Pageable.from(1, 100, Sort.UNSORTED), null, null, Map.of("country", "FR"));
        assertThat(save.size(), is(1));
    }

    @Test
    void findSourceCode() {
        List<SearchResult<Flow>> search = flowRepository.findSourceCode(Pageable.from(1, 10, Sort.UNSORTED), "io.kestra.core.models.conditions.types.MultipleCondition", null);

        assertThat((long) search.size(), is(2L));

        SearchResult<Flow> flow = search
            .stream()
            .filter(flowSearchResult -> flowSearchResult.getModel()
                .getId()
                .equals("trigger-multiplecondition-listener"))
            .findFirst()
            .orElseThrow();
        assertThat(flow.getFragments().get(0), containsString("types.MultipleCondition[/mark]"));
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
                        "type", "io.kestra.core.tasks.log.Log",
                        "level", "invalid"
                    )),
                    "deleted", false
                )))
                .execute();
        });

        Optional<Flow> flow = flowRepository.findById("io.kestra.unittest", "invalid");

        assertThat(flow.isPresent(), is(true));
        assertThat(flow.get(), instanceOf(FlowWithException.class));
        assertThat(((FlowWithException) flow.get()).getException(), containsString("Cannot deserialize value of type `org.slf4j.event.Level`"));
    }

    @BeforeAll
    protected void setup() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}