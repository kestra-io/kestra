package io.kestra.controller.grpc.services;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlowsWithTenant;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.Scheduler;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.scheduler.store.TriggerStateStore;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true, startScheduler = true)
@Property(name = "kestra.queue.message-protection.enabled", value = "true")
@Property(name = "kestra.queue.message-protection.limit", value = "1048576")
class GrpcWorkerControllerServiceIntegrationTest {
    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    private Scheduler scheduler;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private QueueService queueService;

    @Inject
    @Named("cached")
    private TriggerStateStore triggerStateStore;

    @BeforeEach
    void setUp() {
        OversizedPollingTrigger.reset();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @LoadFlowsWithTenant({ "flows/tests/oversized-polling-trigger.yaml" })
    void shouldContinuePollingWhenEvaluationExceedsQueueLimit(String tenantId) {
        assertThat(queueService.getQueueConfiguration().getMessageProtection().getEnabled()).isTrue();
        assertThat(queueService.getQueueConfiguration().getMessageProtection().getLimit()).isEqualTo(1_048_576);
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(scheduler::isActive);

        TriggerId triggerId = TriggerId.of(tenantId, "io.kestra.tests", "oversized-polling-trigger-flow", "oversized-polling-trigger");
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            assertThat(OversizedPollingTrigger.polls()).isGreaterThanOrEqualTo(1);
            assertThat(triggerStateStore.findByIdWithoutAcl(triggerId)).hasValueSatisfying(state -> assertThat(state.isLocked()).isFalse());
        });

        assertThat(executionRepository.findByFlowId(tenantId, "io.kestra.tests", "oversized-polling-trigger-flow", io.micronaut.data.model.Pageable.UNPAGED))
            .isEmpty();
        assertThat(appender.list)
            .filteredOn(event -> event.getFormattedMessage().contains("Unable to publish polling trigger result"))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getLoggerName()).isEqualTo("scheduler." + tenantId + ".io.kestra.tests.oversized-polling-trigger-flow.oversized-polling-trigger");
                assertThat(event.getFormattedMessage()).contains("Unable to publish polling trigger result");
                assertThat(event.getThrowableProxy()).isNotNull();
                assertThat(event.getThrowableProxy().getCause().getClassName()).contains("MessageTooBigException");
            });

        runnerUtils.awaitFlowExecution(tenantId, "io.kestra.tests", "oversized-polling-trigger-flow", Duration.ofSeconds(20));
        assertThat(OversizedPollingTrigger.polls()).isGreaterThanOrEqualTo(2);
        assertThat(executionRepository.findByFlowId(tenantId, "io.kestra.tests", "oversized-polling-trigger-flow", io.micronaut.data.model.Pageable.UNPAGED))
            .hasSize(1);
    }
}
