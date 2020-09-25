package org.kestra.runner.kafka;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.kestra.core.models.templates.Template;

@Slf4j
@KafkaQueueEnabled
@Replaces(org.kestra.core.tasks.flows.Template.MemoryTemplateExecutor.class)
@Requires(property = "kestra.server-type", value = "EXECUTOR")
public class KafkaTemplateExecutor implements org.kestra.core.tasks.flows.Template.TemplateExecutorInterface {
    private final ReadOnlyKeyValueStore<String, Template> store;

    public KafkaTemplateExecutor(ReadOnlyKeyValueStore<String, Template> store) {
        this.store = store;
    }

    public Template findById(String namespace, String templateId) {
        return this.store.get(Template.uid(namespace, templateId));
    }
}
