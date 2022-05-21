package io.kestra.core.repositories;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.templates.Template;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest(transactional = false)
public abstract class AbstractTemplateRepositoryTest {
    @Inject
    protected TemplateRepositoryInterface templateRepository;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        TemplateListener.reset();
    }

    protected static Template.TemplateBuilder<?, ?> builder() {
        return builder(null);
    }

    protected static Template.TemplateBuilder<?, ?> builder(String namespace) {
        return Template.builder()
            .id(IdUtils.create())
            .namespace(namespace == null ? "kestra.test" : namespace)
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()));
    }

    @Test
    void findById() {
        Template template = builder().build();
        templateRepository.create(template);

        Optional<Template> full = templateRepository.findById(template.getNamespace(), template.getId());
        assertThat(full.isPresent(), is(true));

        full.ifPresent(current -> {
            assertThat(full.get().getId(), is(template.getId()));
        });
    }

    @Test
    void findByNamespace() {
        Template template1 = builder().build();
        Template template2 = Template.builder()
            .id(IdUtils.create())
            .namespace("kestra.test.template").build();

        templateRepository.create(template1);
        templateRepository.create(template2);

        List<Template> templates = templateRepository.findByNamespace(template1.getNamespace());
        assertThat(templates.size(), greaterThanOrEqualTo(1));
        templates = templateRepository.findByNamespace(template2.getNamespace());
        assertThat(templates.size(), is(1));
    }

    @Test
    void save() {
        Template template = builder().build();
        Template save = templateRepository.create(template);

        assertThat(save.getId(), is(template.getId()));
    }

    @Test
    void findAll() {
        long saveCount = templateRepository.findAll().size();
        Template template = builder().build();
        templateRepository.create(template);
        long size = templateRepository.findAll().size();
        assertThat(size, greaterThan(saveCount));
        templateRepository.delete(template);
        assertThat((long) templateRepository.findAll().size(), is(saveCount));
    }

    @Test
    void delete() {
        Template template = builder().build();

        Template save = templateRepository.create(template);
        templateRepository.delete(save);

        assertThat(templateRepository.findById(template.getNamespace(), template.getId()).isPresent(), is(false));

        assertThat(TemplateListener.getEmits().size(), is(2));
        assertThat(TemplateListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.CREATE).count(), is(1L));
        assertThat(TemplateListener.getEmits().stream().filter(r -> r.getType() == CrudEventType.DELETE).count(), is(1L));
    }

    @Singleton
    public static class TemplateListener implements ApplicationEventListener<CrudEvent<Template>> {
        private static List<CrudEvent<Template>> emits = new ArrayList<>();

        @Override
        public void onApplicationEvent(CrudEvent<Template> event) {
            emits.add(event);
        }

        public static List<CrudEvent<Template>> getEmits() {
            return emits;
        }

        public static void reset() {
            emits = new ArrayList<>();
        }
    }
}
