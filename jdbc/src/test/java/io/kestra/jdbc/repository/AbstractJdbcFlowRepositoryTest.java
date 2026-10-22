package io.kestra.jdbc.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.AlreadyExistsException;
import io.kestra.core.exceptions.ConflictException;
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

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.kestra.jdbc.repository.AbstractJdbcRepository.field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractJdbcFlowRepositoryTest extends io.kestra.core.repositories.AbstractFlowRepositoryTest {
    private static final int CONCURRENT_WRITERS = 8;

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

    @Test
    void shouldKeepEveryConcurrentUpdateOrRejectItAsConflict() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String flowId = "concurrent-update";
        FlowWithSource previous = flowRepository.create(createTestingLogFlow(tenant, flowId, "v1"));

        try {
            List<Future<FlowWithSource>> updates = runConcurrently(IntStream.range(0, CONCURRENT_WRITERS)
                .<Callable<FlowWithSource>> mapToObj(i -> () -> flowRepository.update(createTestingLogFlow(tenant, flowId, "writer-" + i), previous))
                .toList());

            List<String> saved = new ArrayList<>();
            for (Future<FlowWithSource> update : updates) {
                outcome(update, ConflictException.class).ifPresent(flow -> saved.add(flow.getSource()));
            }
            assertThat(flowRepository.findRevisions(tenant, TEST_NAMESPACE, flowId, false))
                .filteredOn(flow -> flow.getRevision() > previous.getRevision())
                .extracting(FlowWithSource::getSource)
                .containsExactlyInAnyOrderElementsOf(saved);
        } finally {
            deleteFlow(previous);
        }
    }

    @Test
    void shouldCreateAFlowOnceWhenCreatedConcurrently() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String flowId = "concurrent-create";

        List<Future<FlowWithSource>> creates = runConcurrently(IntStream.range(0, CONCURRENT_WRITERS)
            .<Callable<FlowWithSource>> mapToObj(i -> () -> flowRepository.create(createTestingLogFlow(tenant, flowId, "writer-" + i)))
            .toList());

        List<FlowWithSource> created = new ArrayList<>();
        for (Future<FlowWithSource> create : creates) {
            outcome(create, AlreadyExistsException.class).ifPresent(created::add);
        }
        try {
            assertThat(created).hasSize(1);
        } finally {
            created.forEach(this::deleteFlow);
        }
    }

    @Test
    void shouldNotUndoADeleteRacingAnUpdate() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String flowId = "delete-racing-update";
        FlowWithSource previous = flowRepository.create(createTestingLogFlow(tenant, flowId, "v1"));

        try {
            List<Future<FlowWithSource>> writes = runConcurrently(List.of(
                () -> flowRepository.delete(previous),
                () -> flowRepository.update(createTestingLogFlow(tenant, flowId, "v2"), previous)
            ));

            boolean deleted = outcome(writes.getFirst(), ConflictException.class).isPresent();
            outcome(writes.getLast(), ConflictException.class);
            if (deleted) {
                assertThat(flowRepository.findById(tenant, TEST_NAMESPACE, flowId)).isEmpty();
            }
        } finally {
            deleteFlow(previous);
        }
    }

    @Test
    void shouldRejectADeleteAsConflictWhenTheFlowChangedSinceItWasRead() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String flowId = "stale-delete";
        FlowWithSource read = flowRepository.create(createTestingLogFlow(tenant, flowId, "v1"));
        FlowWithSource updated = flowRepository.update(createTestingLogFlow(tenant, flowId, "v2"), read);

        try {
            assertThatThrownBy(() -> flowRepository.delete(read)).isInstanceOf(ConflictException.class);
            assertThatThrownBy(() -> flowRepository.deleteWithoutAcl(read)).isInstanceOf(ConflictException.class);

            flowRepository.delete(updated);
            assertThatThrownBy(() -> flowRepository.delete(updated)).isInstanceOf(ConflictException.class);
            assertThatThrownBy(() -> flowRepository.deleteWithoutAcl(updated)).isInstanceOf(ConflictException.class);
        } finally {
            deleteFlow(updated);
        }
    }

    private static <T> List<Future<T>> runConcurrently(List<Callable<T>> calls) {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(calls.size())) {
            List<Future<T>> futures = calls.stream()
                .map(call -> executor.submit(() -> {
                    start.await();
                    return call.call();
                }))
                .toList();
            start.countDown();
            return futures;
        }
    }

    @SafeVarargs
    private static <T> Optional<T> outcome(Future<T> future, Class<? extends RuntimeException>... expectedFailures) throws InterruptedException {
        try {
            return Optional.of(future.get());
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOfAny(expectedFailures);
            return Optional.empty();
        }
    }
}