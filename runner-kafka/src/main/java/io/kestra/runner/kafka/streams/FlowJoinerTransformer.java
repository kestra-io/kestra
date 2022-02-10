package io.kestra.runner.kafka.streams;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.Executor;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.core.tasks.flows.Template;
import io.kestra.core.utils.Await;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Slf4j
public class FlowJoinerTransformer implements ValueTransformerWithKey<String, Executor, Executor> {
    private final boolean withDefaults;
    private final TaskDefaultService taskDefaultService;
    private final FlowExecutorInterface flowExecutorInterface;
    private final Template.TemplateExecutorInterface templateExecutorInterface;

    public FlowJoinerTransformer(FlowExecutorInterface flowExecutorInterface, Template.TemplateExecutorInterface templateExecutorInterface, boolean withDefaults, TaskDefaultService taskDefaultService) {
        this.flowExecutorInterface = flowExecutorInterface;
        this.templateExecutorInterface = templateExecutorInterface;
        this.withDefaults = withDefaults;
        this.taskDefaultService = taskDefaultService;
    }

    @Override
    public void init(final ProcessorContext context) {

    }

    @Override
    public Executor transform(String key, Executor executor) {
        Flow flow;

        try {
            // pooling of new flow can be delayed on ExecutorStore, we maybe need to wait that the flow is updated
            flow = Await.until(
                () -> flowExecutorInterface.findByExecution(executor.getExecution()).orElse(null),
                Duration.ofMillis(100),
                Duration.ofMinutes(5)
            );
        } catch (TimeoutException e) {
            return executor.withException(
                new Exception("Unable to find flow with namespace: '" + executor.getExecution().getNamespace() + "'" +
                    ", id: '" + executor.getExecution().getFlowId() + "', " +
                    "revision '" + executor.getExecution().getFlowRevision() + "'"),
                "joinFlow"
            );
        }

        if (!withDefaults) {
            return executor.withFlow(flow);
        }

        try {
            flow = Template.injectTemplate(
                flow,
                executor.getExecution(),
                (namespace, id) -> this.templateExecutorInterface.findById(namespace, id).orElse(null)
            );
        } catch (InternalException e) {
            log.warn("Failed to inject template",  e);
        }

        Flow flowWithDefaults = taskDefaultService.injectDefaults(flow, executor.getExecution());

        return executor.withFlow(flowWithDefaults);
    }

    @Override
    public void close() {
    }
}
