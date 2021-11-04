package io.kestra.runner.kafka.streams;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.Executor;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.core.tasks.flows.Template;
import io.kestra.runner.kafka.services.SafeKeyValueStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

import java.util.Optional;

@Slf4j
public class FlowJoinerTransformer implements ValueTransformerWithKey<String, Executor, Executor> {
    private final boolean withDefaults;
    private final TaskDefaultService taskDefaultService;

    private SafeKeyValueStore<String, ValueAndTimestamp<Flow>> flowStore;
    private SafeKeyValueStore<String, ValueAndTimestamp<io.kestra.core.models.templates.Template>> templateStore;

    public FlowJoinerTransformer(boolean withDefaults, TaskDefaultService taskDefaultService) {
        this.withDefaults = withDefaults;
        this.taskDefaultService = taskDefaultService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        var flowStore = (KeyValueStore<String, ValueAndTimestamp<Flow>>) context.getStateStore("flow");
        this.flowStore = new SafeKeyValueStore<>(flowStore, flowStore.name());

        var templateStore = (KeyValueStore<String, ValueAndTimestamp<io.kestra.core.models.templates.Template>>) context.getStateStore("template");
        this.templateStore = new SafeKeyValueStore<>(templateStore, templateStore.name());
    }

    @Override
    public Executor transform(String key, Executor executor) {
        Optional<ValueAndTimestamp<Flow>> flowState = this.flowStore.get(Flow.uid(executor.getExecution()));

        if (flowState.isEmpty() || flowState.get().value() == null) {
            return null;
        }

        Flow flow = flowState.get().value();

        if (!withDefaults) {
            return executor.withFlow(flow);
        }

        flow = Template.injectTemplate(flow, executor.getExecution(), (namespace, id) -> {
            String templateUid = io.kestra.core.models.templates.Template.uid(
                namespace,
                id
            );

            return this.templateStore.get(templateUid).map(ValueAndTimestamp::value).orElse(null);
        });

        Flow flowWithDefaults = taskDefaultService.injectDefaults(flow, executor.getExecution());

        return executor.withFlow(flowWithDefaults);
    }

    @Override
    public void close() {
    }
}
