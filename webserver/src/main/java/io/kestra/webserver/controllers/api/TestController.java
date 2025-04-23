package io.kestra.webserver.controllers.api;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.FlowService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.test.flow.UnitTest;
import io.kestra.core.test.TestSuite;
import io.kestra.core.test.flow.TriggerFixture;
import io.kestra.core.utils.ListUtils;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Validated
@Controller("/api/v1/tests")
public class TestController {
    @Inject
    private FlowService flowService;

    @Inject
    private TenantService tenantService;

    @Inject
    private YamlParser yamlParser;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    private ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher;

    @ExecuteOn(TaskExecutors.IO)
    @Post(consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Flows"}, summary = "Test a flow")
    public void test(@Parameter(description = "The flow namespace") @Body String testStr) throws QueueException, InterruptedException {
        TestSuite testSuite = yamlParser.parse(testStr, TestSuite.class).withSource(testStr);
        UnitTest test = testSuite.getTestCases().getFirst();// TODO...

        Flow flow = flowService.getFlowIfExecutableOrThrow(tenantService.resolveTenant(), testSuite.getNamespace(), testSuite.getFlowId(), Optional.empty());
        Map<String, Object> inputs = ListUtils.emptyOnNull(test.getFixtures().getInputs()).stream().collect(Collectors.toMap(
            input -> input.getId(),
            input -> input.getValue()
        ));
        Execution current = Execution.newExecution(flow, (f, e) -> inputs, null, Optional.empty())
            .withFixtures(test.getFixtures().getTasks());

        TriggerFixture trigger = test.getFixtures().getTrigger();
        if (test.getFixtures().getTrigger() != null) {
            current = current.withTrigger(ExecutionTrigger.builder()
                    .id(trigger.getId())
                    .type(trigger.getType())
                    .variables(trigger.getVariables())
                .build()
            );
        }


        List<Label> existingLabels = ListUtils.emptyOnNull(current.getLabels());
        existingLabels.add(new Label(Label.TEST, "true"));
        current = current.withLabels(existingLabels);

        executionQueue.emit(current);
        eventPublisher.publishEvent(new CrudEvent<>(current, CrudEventType.CREATE));
    }

}
