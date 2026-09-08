package io.kestra.jdbc.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.kestra.jdbc.repository.AbstractJdbcRepository.field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /**
     * A flow stored by Kestra 1.x whose trigger carries the removed `conditions` (or `preconditions`) must not
     * be read back with the property silently dropped: the trigger would then match everything. It surfaces as
     * a {@link FlowWithException}, like a trigger whose type no longer exists.
     */
    @Test
    void shouldReturnFlowWithExceptionForLegacyTriggerConditions() {
        assertLegacyTriggerPropertyIsRejected(
            "legacy-trigger-conditions",
            legacyTrigger(
                "conditions", List.of(
                    Map.of(
                        "type", "io.kestra.plugin.core.condition.ExecutionFlow",
                        "namespace", "io.kestra.unittest",
                        "flowId", "dep-foreach"
                    )
                )
            ),
            "Unrecognized property \"conditions\" on trigger \"on_foreach\""
        );
    }

    @Test
    void shouldReturnFlowWithExceptionForLegacyTriggerPreconditions() {
        assertLegacyTriggerPropertyIsRejected(
            "legacy-trigger-preconditions",
            legacyTrigger("preconditions", Map.of("id", "dep", "flows", List.of(Map.of("namespace", "io.kestra.unittest", "flowId", "dep-foreach")))),
            "Unrecognized property \"preconditions\" on trigger \"on_foreach\""
        );
    }

    /**
     * A pre-2.0 Flow trigger, as an ordered map: the stored flow JSON has the trigger `id` before the removed
     * property, which is what lets the error name the trigger.
     */
    private static Map<String, Object> legacyTrigger(String legacyProperty, Object value) {
        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("id", "on_foreach");
        trigger.put("type", "io.kestra.plugin.core.trigger.Flow");
        trigger.put("states", List.of("SUCCESS", "FAILED"));
        trigger.put(legacyProperty, value);
        return trigger;
    }

    private void assertLegacyTriggerPropertyIsRejected(String flowId, Map<String, Object> trigger, String expectedMessage) {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        dslContextWrapper.transaction(configuration ->
        {
            DSLContext context = DSL.using(configuration);

            context.insertInto(flowRepository.jdbcRepository.getTable())
                .set(field("key"), tenant + "_io.kestra.unittest_" + flowId)
                .set(field("source_code"), "id: " + flowId)
                .set(
                    field("value"), JacksonMapper.ofJson().writeValueAsString(
                        Map.of(
                            "id", flowId,
                            "tenantId", tenant,
                            "namespace", "io.kestra.unittest",
                            "revision", 1,
                            "deleted", false,
                            "tasks", List.of(Map.of("id", "log", "type", "io.kestra.plugin.core.log.Log", "message", "hello")),
                            "triggers", List.of(trigger)
                        )
                    )
                )
                .execute();
        });

        Optional<FlowWithSource> flow = flowRepository.findByIdWithSource(tenant, "io.kestra.unittest", flowId);

        assertThat(flow).isPresent();
        assertThat(flow.get()).isInstanceOf(FlowWithException.class);
        assertThat(((FlowWithException) flow.get()).getException()).contains(expectedMessage);
    }

    @Test
    void shouldRejectUnknownSortField() {
        Pageable pageable = Pageable.from(1, 10, Sort.of(Sort.Order.asc("nonexistent")));

        assertThatThrownBy(() -> flowRepository.find(pageable, MAIN_TENANT, (List<QueryFilter>) null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nonexistent");
    }

    @Test
    void shouldRejectNonSortableInternalColumn() {
        Pageable pageable = Pageable.from(1, 10, Sort.of(Sort.Order.asc("value")));

        assertThatThrownBy(() -> flowRepository.find(pageable, MAIN_TENANT, (List<QueryFilter>) null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectAmbiguousRevisionColumn() {
        // "revision" is a real column but ambiguous in this repository's last-revision join
        Pageable pageable = Pageable.from(1, 10, Sort.of(Sort.Order.asc("revision")));

        assertThatThrownBy(() -> flowRepository.find(pageable, MAIN_TENANT, (List<QueryFilter>) null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldSortRegardlessOfFieldCase() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = flowRepository.create(createTestingLogFlow(tenant, "case-sensitivity-flow", "log"));

        try {
            // the real column is "id"; wrong case previously 500'd because H2 is case-sensitive on quoted identifiers
            Pageable pageable = Pageable.from(1, 10, Sort.of(Sort.Order.desc("ID")));

            assertThatCode(() -> flowRepository.find(pageable, tenant, (List<QueryFilter>) null))
                .doesNotThrowAnyException();
        } finally {
            deleteFlow(flow);
        }
    }

}