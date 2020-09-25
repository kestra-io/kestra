package org.kestra.runner.memory;

import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.templates.Template;
import org.kestra.core.repositories.TemplateRepositoryInterface;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@MemoryQueueEnabled
@Singleton
public class MemoryTemplateExecutor implements org.kestra.core.tasks.flows.Template.TemplateExecutorInterface {
    @Inject
    private TemplateRepositoryInterface templateRepository;

    public Template findById(String namespace, String templateId) {
        return this.templateRepository.findById(namespace, templateId).orElse(null);
    }
}
