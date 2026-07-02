package io.kestra.webserver.controllers.api;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.queues.QueueException;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.scheduler.vnodes.VNodes;
import io.kestra.core.services.FlowService;
import io.kestra.core.tasks.test.PollingTrigger;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.plugin.core.debug.Return;
import io.kestra.webserver.models.api.ApiTriggerAndState;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parameterized tests that exercise the {@code /search} trigger endpoint against the full surface
 * of supported filter fields (including the controller-level
 * {@code dateFilter}/{@code startDate}/{@code endDate} rewrite). The scheduler is deliberately
 * disabled so trigger state fixtures (especially {@code nextEvaluationDate} and
 * {@code lastTriggeredDate}) are not mutated under the test.
 */
@KestraTest(startRunner = true, startScheduler = false)
class TriggerControllerFilterTest {

    public static final String TENANT_ID = "main";
    public static final String TRIGGER_PATH = "/api/v1/main/triggers";

    private static final Instant DATE_OLD = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant DATE_NEW = Instant.parse("2024-06-01T00:00:00Z");
    private static final ZonedDateTime DATE_BETWEEN = ZonedDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneId.of("UTC"));

    private static final String NS_FILTER = "io.kestra.filter";
    private static final String NS_OTHER = "com.other.ns";
    private static final String FLOW_FILTER = "filter-flow";
    private static final String FLOW_OTHER = "other-flow";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    FlowService flowService;

    @Inject
    AbstractJdbcTriggerRepository jdbcTriggerRepository;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    SchedulerConfiguration schedulerConfiguration;

    @BeforeEach
    protected void setup() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    private TriggerState.TriggerStateBuilder fixture(String triggerId, String namespace, String flowId, String workerId) {
        return TriggerState.builder()
            .tenantId(TENANT_ID)
            .triggerId(triggerId)
            .namespace(namespace)
            .flowId(flowId)
            .workerId(workerId)
            .vnode(VNodes.computeVNodeFromFlow(FlowId.of(TENANT_ID, namespace, flowId, null), schedulerConfiguration.vnodes()));
    }

    private Flow flowWithTriggers(String namespace, String flowId, List<String> triggerIds) {
        return Flow.builder()
            .tenantId(TENANT_ID)
            .namespace(namespace)
            .id(flowId)
            .tasks(Collections.singletonList(
                Return.builder()
                    .id("task")
                    .type(Return.class.getName())
                    .format(Property.ofValue("return data"))
                    .build()
            ))
            .triggers(triggerIds.stream().map(id ->
                (AbstractTrigger) PollingTrigger.builder()
                    .id(id)
                    .type(PollingTrigger.class.getName())
                    .build()
            ).toList())
            .build();
    }

    private void seedFilterFixtures() throws FlowProcessingException, QueueException {
        List<String> filterTriggers = List.of(
            "alpha-trigger",
            "nextexec-old", "nextexec-new",
            "ldt-old", "ldt-new",
            "source-schedule", "source-polling",
            "locked-true", "locked-false",
            "state-disabled", "state-enabled"
        );
        flowService.create(GenericFlow.of(flowWithTriggers(NS_FILTER, FLOW_FILTER, filterTriggers)));
        flowService.create(GenericFlow.of(flowWithTriggers(NS_OTHER, FLOW_OTHER, List.of("beta-trig"))));

        // QUERY / NAMESPACE / FLOW_ID / TRIGGER_ID / WORKER_ID
        jdbcTriggerRepository.save(fixture("alpha-trigger", NS_FILTER, FLOW_FILTER, "alpha-worker").build());
        jdbcTriggerRepository.save(fixture("beta-trig", NS_OTHER, FLOW_OTHER, "beta-wrkr").build());

        // NEXT_EXECUTION_DATE
        jdbcTriggerRepository.save(fixture("nextexec-old", NS_FILTER, FLOW_FILTER, "worker").nextEvaluationDate(DATE_OLD).build());
        jdbcTriggerRepository.save(fixture("nextexec-new", NS_FILTER, FLOW_FILTER, "worker").nextEvaluationDate(DATE_NEW).build());

        // LAST_TRIGGERED_DATE
        jdbcTriggerRepository.save(fixture("ldt-old", NS_FILTER, FLOW_FILTER, "worker").lastTriggeredDate(DATE_OLD).build());
        jdbcTriggerRepository.save(fixture("ldt-new", NS_FILTER, FLOW_FILTER, "worker").lastTriggeredDate(DATE_NEW).build());

        // SOURCE
        jdbcTriggerRepository.save(fixture("source-schedule", NS_FILTER, FLOW_FILTER, "worker").type(TriggerType.SCHEDULE).build());
        jdbcTriggerRepository.save(fixture("source-polling", NS_FILTER, FLOW_FILTER, "worker").type(TriggerType.POLLING).build());

        // LOCKED
        jdbcTriggerRepository.save(fixture("locked-true", NS_FILTER, FLOW_FILTER, "worker").locked(true).build());
        jdbcTriggerRepository.save(fixture("locked-false", NS_FILTER, FLOW_FILTER, "worker").locked(false).build());

        // TRIGGER_STATE (disabled flag)
        jdbcTriggerRepository.save(fixture("state-disabled", NS_FILTER, FLOW_FILTER, "worker").disabled(true).build());
        jdbcTriggerRepository.save(fixture("state-enabled", NS_FILTER, FLOW_FILTER, "worker").disabled(false).build());
    }

    public record FilterTestCase(String urlQuery, List<String> expectedTriggerIds) {}

    public static final List<Named<FilterTestCase>> filterTestCases = List.of(
        // --- TRIGGER_ID ---
        Named.of("triggerId EQUALS alpha-trigger",
            new FilterTestCase("filters[triggerId][EQUALS]=alpha-trigger", List.of("alpha-trigger"))),
        Named.of("triggerId STARTS_WITH alpha",
            new FilterTestCase("filters[triggerId][STARTS_WITH]=alpha", List.of("alpha-trigger"))),

        // --- NAMESPACE ---
        Named.of("namespace EQUALS io.kestra.filter",
            new FilterTestCase("filters[namespace][EQUALS]=" + NS_FILTER + "&size=50",
                List.of("alpha-trigger", "nextexec-old", "nextexec-new", "ldt-old", "ldt-new",
                    "source-schedule", "source-polling", "locked-true", "locked-false",
                    "state-disabled", "state-enabled"))),

        // --- FLOW_ID ---
        Named.of("flowId EQUALS other-flow",
            new FilterTestCase("filters[flowId][EQUALS]=" + FLOW_OTHER, List.of("beta-trig"))),

        // --- WORKER_ID ---
        Named.of("workerId EQUALS alpha-worker",
            new FilterTestCase("filters[workerId][EQUALS]=alpha-worker", List.of("alpha-trigger"))),

        // --- SOURCE ---
        Named.of("source EQUALS SCHEDULE",
            new FilterTestCase("filters[source][EQUALS]=SCHEDULE", List.of("source-schedule"))),
        Named.of("source EQUALS POLLING",
            new FilterTestCase("filters[source][EQUALS]=POLLING", List.of("source-polling"))),

        // --- LOCKED ---
        Named.of("locked EQUALS true",
            new FilterTestCase("filters[locked][EQUALS]=true", List.of("locked-true"))),

        // --- NEXT_EXECUTION_DATE ---
        Named.of("nextExecutionDate GREATER_THAN DATE_BETWEEN",
            new FilterTestCase("filters[nextExecutionDate][GREATER_THAN]=" + DATE_BETWEEN,
                List.of("nextexec-new"))),
        Named.of("nextExecutionDate LESS_THAN DATE_BETWEEN",
            new FilterTestCase("filters[nextExecutionDate][LESS_THAN]=" + DATE_BETWEEN,
                List.of("nextexec-old"))),

        // --- LAST_TRIGGERED_DATE ---
        Named.of("lastTriggeredDate GREATER_THAN DATE_BETWEEN",
            new FilterTestCase("filters[lastTriggeredDate][GREATER_THAN]=" + DATE_BETWEEN,
                List.of("ldt-new"))),
        Named.of("lastTriggeredDate LESS_THAN DATE_BETWEEN",
            new FilterTestCase("filters[lastTriggeredDate][LESS_THAN]=" + DATE_BETWEEN,
                List.of("ldt-old"))),

        // --- UI-style URL: startDate/endDate are rewritten by the controller to the chosen target field ---
        Named.of("startDate (no dateFilter) rewrites to nextExecutionDate",
            new FilterTestCase("filters[startDate][GREATER_THAN]=" + DATE_BETWEEN,
                List.of("nextexec-new"))),
        Named.of("startDate with dateFilter=NEXT_EXECUTION_DATE rewrites to nextExecutionDate",
            new FilterTestCase("filters[startDate][GREATER_THAN]=" + DATE_BETWEEN + "&dateFilter=NEXT_EXECUTION_DATE",
                List.of("nextexec-new"))),
        Named.of("startDate with dateFilter=LAST_TRIGGERED_DATE rewrites to lastTriggeredDate",
            new FilterTestCase("filters[startDate][GREATER_THAN]=" + DATE_BETWEEN + "&dateFilter=LAST_TRIGGERED_DATE",
                List.of("ldt-new")))
    );

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @FieldSource("filterTestCases")
    void shouldReturnExpectedTriggersForFilterQuery(FilterTestCase testCase) throws FlowProcessingException, QueueException {
        // Given a catalogue of trigger fixtures covering each filter field
        seedFilterFixtures();

        // When calling /search with the parameterized filter URL
        PagedResults<ApiTriggerAndState> triggers = client.toBlocking().retrieve(
            HttpRequest.GET(TRIGGER_PATH + "/search?" + testCase.urlQuery()),
            Argument.of(PagedResults.class, ApiTriggerAndState.class)
        );

        // Then only the expected trigger IDs are returned
        assertThat(triggers.getResults().stream().map(t -> t.state().triggerId()).toList())
            .containsExactlyInAnyOrderElementsOf(testCase.expectedTriggerIds());
    }
}
