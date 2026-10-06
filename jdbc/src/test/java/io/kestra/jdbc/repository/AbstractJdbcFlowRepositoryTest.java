package io.kestra.jdbc.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.micronaut.data.model.Pageable;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.kestra.jdbc.repository.AbstractJdbcRepository.field;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJdbcFlowRepositoryTest extends io.kestra.core.repositories.AbstractFlowRepositoryTest {
    @Inject
    protected AbstractJdbcFlowRepository flowRepository;

    @Inject
    protected JooqDSLContextWrapper dslContextWrapper;

    @Disabled("Test disabled: no exception thrown when converting to dynamic properties")
    @Test
    public void invalidFlow() {
        dslContextWrapper.transaction(configuration ->
        {
            DSLContext context = DSL.using(configuration);

            context.insertInto(flowRepository.jdbcRepository.getTable())
                .set(field("key"), "io.kestra.unittest_invalid")
                .set(field("source_code"), "")
                .set(
                    field("value"), JacksonMapper.ofJson().writeValueAsString(
                        Map.of(
                            "id", "invalid",
                            "namespace", "io.kestra.unittest",
                            "revision", 1,
                            "tasks", List.of(
                                Map.of(
                                    "id", "invalid",
                                    "type", "io.kestra.plugin.core.log.Log",
                                    "level", "invalid"
                                )
                            ),
                            "deleted", false
                        )
                    )
                )
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

    @Test
    void invalidFlowShouldPreserveLabels() {
        String flowId = "invalid-labels-" + IdUtils.create();
        String namespace = "io.kestra.unittest";
        String sourceCode = """
            id: %s
            namespace: %s
            labels:
              system.readOnly: "true"
            tasks:
              - id: broken
                type: io.kestra.invalid.Unknown
            """.formatted(flowId, namespace);

        dslContextWrapper.transaction(configuration ->
        {
            DSLContext context = DSL.using(configuration);

            context.insertInto(flowRepository.jdbcRepository.getTable())
                .set(field("key"), namespace + "_" + flowId)
                .set(field("source_code"), sourceCode)
                .set(
                    field("value"), (Object) JSONB.valueOf(JacksonMapper.ofJson().writeValueAsString(
                        Map.of(
                            "id", flowId,
                            "namespace", namespace,
                            "tenantId", MAIN_TENANT,
                            "revision", 1,
                            "deleted", false,
                            "disabled", false,
                            "labels", List.of(
                                Map.of("key", "system.readOnly", "value", "true"),
                                Map.of("key", "team", "value", "platform")
                            ),
                            "tasks", List.of(
                                Map.of(
                                    "id", "broken",
                                    "type", "io.kestra.invalid.Unknown"
                                )
                            ),
                            "source", sourceCode
                        )
                    ))
                )
                .execute();
        });

        Optional<FlowWithSource> flow = flowRepository.findByIdWithSource(MAIN_TENANT, namespace, flowId);

        try {
            assertThat(flow.isPresent()).isTrue();
            assertThat(flow.get()).isInstanceOf(FlowWithException.class);
            assertThat(flow.get().getLabels()).containsExactlyInAnyOrder(
                new Label("system.readOnly", "true"),
                new Label("team", "platform")
            );
            assertThat(flow.get().getSource()).contains(flowId);

            ArrayListTotal<Flow> filtered = flowRepository.find(
                Pageable.UNPAGED, MAIN_TENANT, List.of(
                    QueryFilter.builder()
                        .field(QueryFilter.Field.LABELS)
                        .value(Map.of("team", "platform"))
                        .operation(QueryFilter.Op.EQUALS)
                        .build(),
                    QueryFilter.builder()
                        .field(QueryFilter.Field.FLOW_ID)
                        .value(flowId)
                        .operation(QueryFilter.Op.EQUALS)
                        .build()
                )
            );

            assertThat(filtered).extracting(Flow::getId).contains(flowId);
            assertThat(filtered.stream().filter(f -> f.getId().equals(flowId)).findFirst().orElseThrow().getLabels())
                .contains(new Label("team", "platform"));
        } finally {
            flow.ifPresent(value -> flowRepository.delete(value));
        }
    }

}