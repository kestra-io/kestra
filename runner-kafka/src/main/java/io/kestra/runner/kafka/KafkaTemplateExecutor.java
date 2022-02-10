package io.kestra.runner.kafka;

import io.kestra.core.models.templates.Template;
import io.kestra.core.utils.Await;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@KafkaQueueEnabled
@Replaces(io.kestra.core.tasks.flows.Template.MemoryTemplateExecutor.class)
@Requires(property = "kestra.server-type", pattern = "(EXECUTOR|STANDALONE)")
@Singleton
public class KafkaTemplateExecutor implements io.kestra.core.tasks.flows.Template.TemplateExecutorInterface {
    private Map<String, Template> templates;


    public synchronized void setTemplates(List<Template> templates) {
        this.templates = templates
            .stream()
            .map(template -> new AbstractMap.SimpleEntry<>(
                template.uid(),
                template
            ))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SneakyThrows
    private void await() {
        if (templates == null) {
            Await.until(() -> this.templates != null, Duration.ofMillis(100), Duration.ofMinutes(5));
        }
    }

    public Optional<Template> findById(String namespace, String templateId) {
        this.await();

        return Optional.ofNullable(this.templates.get(Template.uid(namespace, templateId)));
    }
}
