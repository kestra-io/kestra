package org.kestra.repository.memory;

import com.devskiller.friendly_id.FriendlyId;
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.templates.Template;
import org.kestra.core.repositories.TemplateRepositoryInterface;
import org.kestra.core.tasks.debugs.Return;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

@MicronautTest
public class MemoryTemplateRepositoryTest {
    @Inject
    protected TemplateRepositoryInterface templateRepository;

    private static Template.TemplateBuilder builder() {
        return Template.builder()
            .id(FriendlyId.createFriendlyId())
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()));
    }

    @Test
    void findById() {
        Template template = builder().build();
        templateRepository.create(template);

        Optional<Template> full = templateRepository.findById(template.getId());
        assertThat(full.isPresent(), is(true));

        full.ifPresent(current -> {
            assertThat(full.get().getId(), is(template.getId()));
        });
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

        assertThat(templateRepository.findById(template.getId()).isPresent(), is(false));
    }
}
