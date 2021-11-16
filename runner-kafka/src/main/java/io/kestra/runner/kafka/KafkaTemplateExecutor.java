package io.kestra.runner.kafka;

import io.kestra.runner.kafka.services.SafeKeyValueStore;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import io.kestra.core.models.templates.Template;

@Slf4j
@KafkaQueueEnabled
@Replaces(io.kestra.core.tasks.flows.Template.MemoryTemplateExecutor.class)
@Requires(property = "kestra.server-type", value = "EXECUTOR")
public class KafkaTemplateExecutor implements io.kestra.core.tasks.flows.Template.TemplateExecutorInterface {
    private final SafeKeyValueStore<String, Template> store;

    public KafkaTemplateExecutor(ReadOnlyKeyValueStore<String, Template> store, String name) {
        this.store = new SafeKeyValueStore<>(store, name);
    }

    public Template findById(String namespace, String templateId) {
        return this.store.get(Template.uid(namespace, templateId)).orElse(null);
    }
}
