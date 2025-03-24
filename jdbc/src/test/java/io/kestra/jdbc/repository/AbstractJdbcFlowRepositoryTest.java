package io.kestra.jdbc.repository;

import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @Disabled("Test disabled: no exception thrown when converting to dynamic properties")
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

        Optional<FlowWithSource> flow = flowRepository.findByIdWithSource(null, "io.kestra.unittest", "invalid");

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
}