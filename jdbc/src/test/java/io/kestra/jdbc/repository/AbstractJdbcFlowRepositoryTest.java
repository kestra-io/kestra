package io.kestra.jdbc.repository;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.kestra.jdbc.repository.AbstractJdbcRepository.field;
import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.jdbc.JooqDSLContextWrapper;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public abstract class AbstractJdbcFlowRepositoryTest extends io.kestra.core.repositories.AbstractFlowRepositoryTest {
    @Inject
    protected AbstractJdbcFlowRepository flowRepository;

    @Inject
    protected JooqDSLContextWrapper dslContextWrapper;

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

        Optional<FlowWithSource> flow = flowRepository.findByIdWithSource(MAIN_TENANT, "io.kestra.unittest", "invalid");

        try {
            assertThat(flow.isPresent()).isTrue();
            assertThat(flow.get()).isInstanceOf(FlowWithException.class);
            assertThat(((FlowWithException) flow.get()).getException()).contains("Cannot deserialize value of type `org.slf4j.event.Level`");
        } finally {
            flow.ifPresent(value -> flowRepository.delete(value));
        }
    }

}