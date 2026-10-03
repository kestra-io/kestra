package io.kestra.plugin.core.trigger;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.services.WebhookService;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class BatchWebhookTest {
    @Inject
    WebhookService webhookService;

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    RunContextInitializer runContextInitializer;

    @Inject
    ModelValidator modelValidator;

    @Test
    void shouldRejectWhenBothEventsCountAndPollingIntervalAreNull() {
        // Given
        BatchWebhook invalid = BatchWebhook.builder()
            .id("batch")
            .type(BatchWebhook.class.getName())
            .key("secret-key")
            .build();

        // When / Then
        assertThat(modelValidator.isValid(invalid)).isPresent();
        assertThat(modelValidator.isValid(invalid).get().getMessage())
            .contains("at least one of 'eventsCount' or 'pollingInterval'");
    }

    @Test
    void shouldAcceptWhenEventsCountIsSet() {
        // Given
        BatchWebhook validCount = BatchWebhook.builder()
            .id("batch")
            .type(BatchWebhook.class.getName())
            .key("secret-key")
            .eventsCount(10)
            .build();

        // When / Then
        assertThat(modelValidator.isValid(validCount)).isEmpty();
    }

    @Test
    void shouldAcceptWhenPollingIntervalIsSet() {
        // Given
        BatchWebhook validInterval = BatchWebhook.builder()
            .id("batch")
            .type(BatchWebhook.class.getName())
            .key("secret-key")
            .pollingInterval(Duration.ofMinutes(15))
            .build();

        // When / Then
        assertThat(modelValidator.isValid(validInterval)).isEmpty();
    }

    @Test
    void shouldRejectWhenPollingIntervalIsZero() {
        // Given
        BatchWebhook zeroInterval = BatchWebhook.builder()
            .id("batch")
            .type(BatchWebhook.class.getName())
            .key("secret-key")
            .pollingInterval(Duration.ZERO)
            .build();

        // When / Then
        assertThat(modelValidator.isValid(zeroInterval)).isPresent();
        assertThat(modelValidator.isValid(zeroInterval).get().getMessage())
            .contains("'pollingInterval' must be strictly positive");
    }

    @Test
    void shouldRejectWhenEventsCountIsLessThanOne() {
        // Given
        BatchWebhook zeroCount = BatchWebhook.builder()
            .id("batch")
            .type(BatchWebhook.class.getName())
            .key("secret-key")
            .eventsCount(0)
            .build();

        // When / Then
        assertThat(modelValidator.isValid(zeroCount)).isPresent();
        assertThat(modelValidator.isValid(zeroCount).get().getMessage())
            .contains("'eventsCount' must be greater than or equal to 1");
    }

    @Test
    void shouldAcceptWhenBothEventsCountAndPollingIntervalAreSet() {
        // Given
        BatchWebhook both = BatchWebhook.builder()
            .id("batch")
            .type(BatchWebhook.class.getName())
            .key("secret-key")
            .eventsCount(10)
            .pollingInterval(Duration.ofMinutes(15))
            .build();

        // When / Then
        assertThat(modelValidator.isValid(both)).isEmpty();
    }

    @Test
    void shouldBufferEventsWithoutStartingExecution() throws Exception {
        // Given
        BatchWebhook trigger = BatchWebhook.builder()
            .id("batch-buffer")
            .type(BatchWebhook.class.getName())
            .key("testkey")
            .eventsCount(5)
            .build();
        Flow flow = flowFor(trigger);

        // When
        HttpResponse<?> response = evaluateWebhook(flow, trigger, "{\"hello\":\"world\"}");

        // Then
        assertThat(response.getStatus().getCode()).isEqualTo(202);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("status")).isEqualTo("buffered");
        assertThat(body.get("eventsBuffered")).isEqualTo(1);
    }

    @Test
    void shouldFlushWhenEventsCountIsReached() throws Exception {
        // Given
        BatchWebhook trigger = BatchWebhook.builder()
            .id("batch-count-" + IdUtils.create())
            .type(BatchWebhook.class.getName())
            .key("testkey")
            .eventsCount(3)
            .build();
        Flow flow = flowFor(trigger);

        // When
        for (int i = 0; i < 3; i++) {
            HttpResponse<?> response = evaluateWebhook(flow, trigger, "{\"n\":" + i + "}");
            assertThat(response.getStatus().getCode()).isEqualTo(202);
        }
        Optional<TriggerEvaluationResult> result = evaluatePolling(flow, trigger);

        // Then
        assertThat(result).isPresent();
        Execution execution = result.get().toExecution(triggerContext(flow, trigger));
        assertThat(execution.getTrigger().getVariables()).containsKeys("uri", "count");
        assertThat(execution.getTrigger().getVariables().get("count")).isEqualTo(3);
        assertThat(evaluatePolling(flow, trigger)).isEmpty();
    }

    @Test
    void shouldLeaveRemainderWhenMoreEventsThanCount() throws Exception {
        // Given
        BatchWebhook trigger = BatchWebhook.builder()
            .id("batch-remainder-" + IdUtils.create())
            .type(BatchWebhook.class.getName())
            .key("testkey")
            .eventsCount(2)
            .build();
        Flow flow = flowFor(trigger);

        // When
        for (int i = 0; i < 3; i++) {
            evaluateWebhook(flow, trigger, "{\"n\":" + i + "}");
        }
        Optional<TriggerEvaluationResult> first = evaluatePolling(flow, trigger);

        // Then
        assertThat(first).isPresent();
        assertThat(first.get().toExecution(triggerContext(flow, trigger)).getTrigger().getVariables().get("count"))
            .isEqualTo(2);
        // Remainder (1 event) is below eventsCount, so no second flush yet.
        assertThat(evaluatePolling(flow, trigger)).isEmpty();
    }

    @Test
    void shouldFlushOnIntervalWhenAtLeastOneEvent() throws Exception {
        // Given
        BatchWebhook trigger = BatchWebhook.builder()
            .id("batch-interval-" + IdUtils.create())
            .type(BatchWebhook.class.getName())
            .key("testkey")
            .pollingInterval(Duration.ofMillis(1))
            .build();
        Flow flow = flowFor(trigger);

        // When
        evaluateWebhook(flow, trigger, "{\"hello\":\"interval\"}");
        Optional<TriggerEvaluationResult> result = evaluatePolling(flow, trigger);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().toExecution(triggerContext(flow, trigger)).getTrigger().getVariables().get("count"))
            .isEqualTo(1);
    }

    @Test
    void shouldWriteIonFileWithOneRowPerEvent() throws Exception {
        // Given
        BatchWebhook trigger = BatchWebhook.builder()
            .id("batch-ion-" + IdUtils.create())
            .type(BatchWebhook.class.getName())
            .key("testkey")
            .eventsCount(2)
            .build();
        Flow flow = flowFor(trigger);

        // When
        evaluateWebhook(flow, trigger, "{\"a\":1}");
        evaluateWebhook(flow, trigger, "{\"a\":2}");
        Optional<TriggerEvaluationResult> result = evaluatePolling(flow, trigger);

        // Then
        assertThat(result).isPresent();
        Execution execution = result.get().toExecution(triggerContext(flow, trigger));
        Object uriObj = execution.getTrigger().getVariables().get("uri");
        assertThat(uriObj).isNotNull();
        URI uri = URI.create(uriObj.toString());
        RunContext runContext = runContextFactory.of(flow, execution);
        List<Object> rows = new ArrayList<>();
        try (InputStream is = new BufferedInputStream(runContext.storage().getFile(uri), FileSerde.BUFFER_SIZE)) {
            FileSerde.readAll(is).doOnNext(rows::add).blockLast();
        }
        assertThat(rows).hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) rows.getFirst();
        assertThat(first).containsKeys("eventId", "eventReceived", "body");
        assertThat(first.get("body")).isInstanceOf(Map.class);
    }

    @Test
    void shouldRejectEventWhenEventFilterIsFalse() throws Exception {
        // Given
        BatchWebhook trigger = BatchWebhook.builder()
            .id("batch-filter-" + IdUtils.create())
            .type(BatchWebhook.class.getName())
            .key("testkey")
            .eventsCount(1)
            .eventFilter("{{ trigger.body.hello == 'world' }}")
            .build();
        Flow flow = flowFor(trigger);

        // When / Then — non-matching event discarded
        HttpResponse<?> rejected = evaluateWebhook(flow, trigger, "{\"hello\":\"nope\"}");
        assertThat(rejected.getStatus().getCode()).isEqualTo(204);
        assertThat(evaluatePolling(flow, trigger)).isEmpty();

        // Matching event buffered and flushed
        HttpResponse<?> accepted = evaluateWebhook(flow, trigger, "{\"hello\":\"world\"}");
        assertThat(accepted.getStatus().getCode()).isEqualTo(202);
        assertThat(evaluatePolling(flow, trigger)).isPresent();
    }

    @Test
    void shouldFlushOnIntervalWhenBothCountAndIntervalAreSetAndCountNotReached() throws Exception {
        // Given — count is high so only the interval path can flush
        BatchWebhook trigger = BatchWebhook.builder()
            .id("batch-both-" + IdUtils.create())
            .type(BatchWebhook.class.getName())
            .key("testkey")
            .eventsCount(100)
            .pollingInterval(Duration.ofMillis(1))
            .build();
        Flow flow = flowFor(trigger);

        // When
        evaluateWebhook(flow, trigger, "{\"n\":1}");
        // firstEventAt is set at accept time; wait past pollingInterval
        Thread.sleep(5);
        Optional<TriggerEvaluationResult> result = evaluatePolling(flow, trigger);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().toExecution(triggerContext(flow, trigger)).getTrigger().getVariables().get("count"))
            .isEqualTo(1);
    }

    @Test
    void shouldNotFlushOnIntervalBeforePollingIntervalElapsedWhenBothSet() throws Exception {
        // Given
        BatchWebhook trigger = BatchWebhook.builder()
            .id("batch-both-wait-" + IdUtils.create())
            .type(BatchWebhook.class.getName())
            .key("testkey")
            .eventsCount(100)
            .pollingInterval(Duration.ofHours(1))
            .build();
        Flow flow = flowFor(trigger);

        // When
        evaluateWebhook(flow, trigger, "{\"n\":1}");

        // Then — one event, count not reached, interval not elapsed
        assertThat(evaluatePolling(flow, trigger)).isEmpty();
    }

    @Test
    void getIntervalShouldPreferCountPollWhenEventsCountSet() {
        // Given / When / Then
        BatchWebhook countOnly = BatchWebhook.builder()
            .id("c")
            .type(BatchWebhook.class.getName())
            .key("k")
            .eventsCount(10)
            .build();
        assertThat(countOnly.getInterval()).isEqualTo(Duration.ofSeconds(1));

        BatchWebhook intervalOnly = BatchWebhook.builder()
            .id("i")
            .type(BatchWebhook.class.getName())
            .key("k")
            .pollingInterval(Duration.ofMinutes(15))
            .build();
        assertThat(intervalOnly.getInterval()).isEqualTo(Duration.ofMinutes(15));

        BatchWebhook both = BatchWebhook.builder()
            .id("b")
            .type(BatchWebhook.class.getName())
            .key("k")
            .eventsCount(10)
            .pollingInterval(Duration.ofMinutes(15))
            .build();
        assertThat(both.getInterval()).isEqualTo(Duration.ofSeconds(1));
    }

    private Flow flowFor(BatchWebhook trigger) {
        return Flow.builder()
            .id("batch-webhook-flow-" + IdUtils.create())
            .namespace("io.kestra.tests")
            .revision(1)
            .tenantId("main")
            .triggers(List.of(trigger))
            .build();
    }

    private HttpResponse<?> evaluateWebhook(Flow flow, BatchWebhook trigger, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.builder()
            .uri(URI.create("/api/v1/main/executions/webhook/" + flow.getNamespace() + "/" + flow.getId() + "/testkey"))
            .addHeader("Content-Type", "application/json")
            .body(HttpRequest.StringRequestBody.builder().content(jsonBody).build())
            .build();
        WebhookContext webhookContext = new WebhookContext(request, null, flow, trigger, webhookService);
        return Objects.requireNonNull(trigger.evaluate(webhookContext).block());
    }

    private Optional<TriggerEvaluationResult> evaluatePolling(Flow flow, BatchWebhook trigger) throws Exception {
        TriggerContext triggerContext = triggerContext(flow, trigger);
        DefaultRunContext runContext = runContextInitializer.forScheduler(
            (DefaultRunContext) runContextFactory.of(flow, trigger),
            triggerContext,
            trigger
        );
        ConditionContext conditionContext = ConditionContext.builder()
            .runContext(runContext)
            .flow(flow)
            .build();
        return trigger.eval(conditionContext, triggerContext);
    }

    private TriggerContext triggerContext(Flow flow, BatchWebhook trigger) {
        return TriggerContext.builder()
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .triggerId(trigger.getId())
            .date(ZonedDateTime.now())
            .build();
    }
}
