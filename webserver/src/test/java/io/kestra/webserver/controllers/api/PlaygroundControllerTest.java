package io.kestra.webserver.controllers.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlowsWithTenant;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.controllers.api.PlaygroundController.ContextResponse;
import io.kestra.webserver.controllers.api.PlaygroundController.ReusedResponse;
import io.kestra.webserver.services.playground.PlaygroundFreshReason;
import io.kestra.webserver.services.playground.PlaygroundPlan;
import io.kestra.webserver.tenants.TenantValidationFilter;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest(startRunner = true)
@Property(name = "kestra.playground.heartbeat.timeout", value = "PT3S")
@Property(name = "kestra.playground.heartbeat.check-interval", value = "1s")
class PlaygroundControllerTest {
    private static final String NAMESPACE = "io.kestra.tests";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    ExecutionRepositoryInterface executionRepository;

    @Inject
    TenantService tenantService;

    @MockBean(TenantService.class)
    TenantService tenantService() {
        return mock(TenantService.class);
    }

    @MockBean(TenantValidationFilter.class)
    TenantValidationFilter tenantValidationFilter() {
        return mock(TenantValidationFilter.class);
    }

    @Test
    @LoadFlowsWithTenant({ "flows/valids/playground-session.yaml" })
    void shouldReuseTheLatestRunAndEndItWhenTheNextRunStarts(String tenantId) {
        when(tenantService.resolveTenant()).thenReturn(tenantId);
        Execution first = runUntil(tenantId, "playground-session", "b", e -> e.getState().isBreakpoint());

        PlaygroundPlan plan = plan(tenantId, "playground-session", "b");
        assertThat(plan.freshReason()).isEqualTo(PlaygroundFreshReason.NONE);
        assertThat(plan.buildOnExecutionId()).isEqualTo(first.getId());
        assertThat(plan.reusedTaskIds()).containsExactly("a");
        assertThat(plan.breakpoints()).containsExactly("c");

        Execution second = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/%s/executions/%s/actions/replay?taskRunId=%s&breakpoints=c".formatted(tenantId, first.getId(), plan.replayFromTaskRunId()), Map.of()),
            Execution.class
        );
        awaitRun(tenantId, second.getId(), e -> e.getState().isBreakpoint());

        assertThat(awaitRun(tenantId, first.getId(), e -> e.getState().isTerminated()).getState().getCurrent()).isEqualTo(State.Type.KILLED);
        ReusedResponse reused = client.toBlocking().retrieve(
            HttpRequest.GET(playground(tenantId, "playground-session") + "/runs/" + second.getId() + "/reused"),
            ReusedResponse.class
        );
        String reusedA = executionRepository.findById(tenantId, second.getId()).orElseThrow().findTaskRunsByTaskId("a").getFirst().getId();
        assertThat(reused.taskRuns().get(reusedA).executionId()).isEqualTo(first.getId());
    }

    @Test
    @LoadFlowsWithTenant({ "flows/valids/playground-session.yaml" })
    void shouldKillTheRunAndRunAfterExecutionOnlyWhenItsLastPanelCloses(String tenantId) {
        when(tenantService.resolveTenant()).thenReturn(tenantId);
        Execution run = runUntil(tenantId, "playground-session", "b", e -> e.getState().isBreakpoint());
        String clients = playground(tenantId, "playground-session") + "/runs/" + run.getId() + "/clients/";

        client.toBlocking().exchange(HttpRequest.POST(clients + "tab-1", Map.of()));
        client.toBlocking().exchange(HttpRequest.POST(clients + "tab-2", Map.of()));
        client.toBlocking().exchange(HttpRequest.DELETE(clients + "tab-1"));
        assertThat(executionRepository.findById(tenantId, run.getId()).orElseThrow().getState().isBreakpoint()).isTrue();

        client.toBlocking().exchange(HttpRequest.DELETE(clients + "tab-2"));
        Execution killed = awaitRun(
            tenantId, run.getId(), e -> e.getState().isTerminated() && !e.findTaskRunsByTaskId("after").isEmpty()
                && e.findTaskRunsByTaskId("after").getFirst().getState().isTerminated()
        );

        assertThat(killed.getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(killed.findTaskRunsByTaskId("after").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlowsWithTenant({ "flows/valids/playground-session.yaml" })
    void shouldKillARunNoPanelReportsAfterTheTimeout(String tenantId) {
        when(tenantService.resolveTenant()).thenReturn(tenantId);
        when(tenantService.listTenants()).thenReturn(List.of(tenantId));
        Execution run = runUntil(tenantId, "playground-session", "b", e -> e.getState().isBreakpoint());

        assertThat(awaitRun(tenantId, run.getId(), e -> e.getState().isTerminated()).getState().getCurrent()).isEqualTo(State.Type.KILLED);
    }

    @Test
    @LoadFlowsWithTenant({ "flows/valids/playground-concurrency.yaml" })
    void shouldFreeTheConcurrencySlotForAQueuedRunWhenANewPlaygroundRunStarts(String tenantId) {
        when(tenantService.resolveTenant()).thenReturn(tenantId);
        runUntil(tenantId, "playground-concurrency", "b", e -> e.getState().isBreakpoint());
        Execution production = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/%s/executions/%s/playground-concurrency".formatted(tenantId, NAMESPACE), null).contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        );
        awaitRun(tenantId, production.getId(), e -> State.Type.QUEUED == e.getState().getCurrent());

        runUntil(tenantId, "playground-concurrency", "b", _ -> true);

        assertThat(awaitRun(tenantId, production.getId(), e -> e.getState().isTerminated()).getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlowsWithTenant({ "flows/valids/playground-session.yaml" })
    void shouldShowWhatTheStoppedTaskSeesWithoutTheServerVariables(String tenantId) {
        when(tenantService.resolveTenant()).thenReturn(tenantId);
        Execution run = runUntil(tenantId, "playground-session", "b", e -> e.getState().isBreakpoint());
        String stopped = run.findTaskRunsByTaskId("b").getFirst().getId();

        ContextResponse context = client.toBlocking().retrieve(
            HttpRequest.GET(playground(tenantId, "playground-session") + "/runs/" + run.getId() + "/context/" + stopped),
            ContextResponse.class
        );

        assertThat(context.variables()).containsKeys("outputs", "taskrun", "flow").doesNotContainKeys("envs", "globals", "kestra");
        assertThat(context.variables().get("outputs")).asInstanceOf(InstanceOfAssertFactories.MAP).containsEntry("a", Map.of("value", "a"));
    }

    @Test
    @LoadFlowsWithTenant({ "flows/valids/playground-session.yaml", "flows/valids/playground-concurrency.yaml" })
    void shouldNotFindARunThroughAnotherFlow(String tenantId) {
        when(tenantService.resolveTenant()).thenReturn(tenantId);
        Execution run = runUntil(tenantId, "playground-session", "b", e -> e.getState().isBreakpoint());

        assertThatThrownBy(() -> client.toBlocking().retrieve(HttpRequest.GET(playground(tenantId, "playground-concurrency") + "/runs/" + run.getId() + "/reused")))
            .isInstanceOfSatisfying(HttpClientResponseException.class, e -> assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode()));
        assertThat(
            client.toBlocking().retrieve(
                HttpRequest.GET(playground(tenantId, "playground-session") + "/runs"),
                Argument.mapOf(String.class, Object.class)
            ).get("total")
        ).isEqualTo(1);
    }

    private Execution runUntil(String tenantId, String flowId, String stopBefore, Predicate<Execution> until) {
        Execution created = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/%s/executions/%s/%s?kind=PLAYGROUND&breakpoints=%s".formatted(tenantId, NAMESPACE, flowId, stopBefore), null)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        );
        return awaitRun(tenantId, created.getId(), until);
    }

    private PlaygroundPlan plan(String tenantId, String flowId, String taskId) {
        return client.toBlocking().retrieve(
            HttpRequest.POST(playground(tenantId, flowId) + "/plan", Map.of("revision", 1, "taskId", taskId, "mode", "UP_TO")),
            PlaygroundPlan.class
        );
    }

    private static String playground(String tenantId, String flowId) {
        return "/api/v1/%s/flows/%s/%s/playground".formatted(tenantId, NAMESPACE, flowId);
    }

    private Execution awaitRun(String tenantId, String executionId, Predicate<Execution> until) {
        return await()
            .atMost(Duration.ofSeconds(30))
            .with().pollDelay(Duration.ofMillis(100)).pollInterval(Duration.ofMillis(250))
            .until(() -> executionRepository.findById(tenantId, executionId).orElse(null), execution -> execution != null && until.test(execution));
    }
}
