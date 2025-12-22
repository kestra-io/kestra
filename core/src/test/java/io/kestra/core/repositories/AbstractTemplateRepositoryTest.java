package io.kestra.core.repositories;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.templates.Template;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public abstract class AbstractTemplateRepositoryTest {
    @Inject
    protected TemplateRepositoryInterface templateRepository;

    @BeforeAll
    protected static void init() throws IOException, URISyntaxException {
        TemplateListener.reset();
    }

    protected static Template.TemplateBuilder<?, ?> builder(String tenantId) {
        return builder(tenantId, null);
    }

    protected static Template.TemplateBuilder<?, ?> builder(String tenantId, String namespace) {
        return Template.builder()
            .id(IdUtils.create())
            .namespace(namespace == null ? "kestra.test" : namespace)
            .tenantId(tenantId)
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format(Property.ofValue("test")).build()));
    }

    @Test
    void findById() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Template template = builder(tenant).build();
        templateRepository.create(template);

        Optional<Template> full = templateRepository.findById(tenant, template.getNamespace(), template.getId());
        assertThat(full.isPresent()).isTrue();
        assertThat(full.get().getId()).isEqualTo(template.getId());

        full = templateRepository.findById(tenant, template.getNamespace(), template.getId());
        assertThat(full.isPresent()).isTrue();
        assertThat(full.get().getId()).isEqualTo(template.getId());
    }

    @Test
    void findByNamespace() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Template template1 = builder(tenant).build();
        Template template2 = Template.builder()
            .id(IdUtils.create())
            .tenantId(tenant)
            .namespace("kestra.test.template").build();

        templateRepository.create(template1);
        templateRepository.create(template2);

        List<Template> templates = templateRepository.findByNamespace(tenant, template1.getNamespace());
        assertThat(templates.size()).isGreaterThanOrEqualTo(1);
        templates = templateRepository.findByNamespace(tenant, template2.getNamespace());
        assertThat(templates.size()).isEqualTo(1);
    }

    @Test
    void save() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Template template = builder(tenant).build();
        Template save = templateRepository.create(template);

        assertThat(save.getId()).isEqualTo(template.getId());
    }

    @Test
    void findAll() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        long saveCount = templateRepository.findAll(tenant).size();
        Template template = builder(tenant).build();
        templateRepository.create(template);
        long size = templateRepository.findAll(tenant).size();
        assertThat(size).isGreaterThan(saveCount);
        templateRepository.delete(template);
        assertThat((long) templateRepository.findAll(tenant).size()).isEqualTo(saveCount);
    }

    @Test
    void findAllForAllTenants() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        long saveCount = templateRepository.findAllForAllTenants().size();
        Template template = builder(tenant).build();
        templateRepository.create(template);
        long size = templateRepository.findAllForAllTenants().size();
        assertThat(size).isGreaterThan(saveCount);
    }

    @Test
    void find() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Template template1 = builder(tenant).build();
        templateRepository.create(template1);
        Template template2 = builder(tenant).build();
        templateRepository.create(template2);
        Template template3 = builder(tenant).build();
        templateRepository.create(template3);

        // with pageable
        List<Template> save = templateRepository.find(Pageable.from(1, 10),null, tenant, "kestra.test");
        assertThat((long) save.size()).isGreaterThanOrEqualTo(3L);

        // without pageable
        save = templateRepository.find(null, tenant, "kestra.test");
        assertThat((long) save.size()).isGreaterThanOrEqualTo(3L);

        templateRepository.delete(template1);
        templateRepository.delete(template2);
        templateRepository.delete(template3);
    }

    private static final Logger LOG = LoggerFactory.getLogger(AbstractTemplateRepositoryTest.class);

    @Test
    protected void delete() throws TimeoutException {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Template template = builder(tenant).build();

        Template save = templateRepository.create(template);
        templateRepository.delete(save);

        assertThat(templateRepository.findById(tenant, template.getNamespace(), template.getId()).isPresent()).isFalse();

        Await.until(() -> {
            LOG.info("-------------> number of event: {}", TemplateListener.getEmits(tenant).size());
            return TemplateListener.getEmits(tenant).size() == 2;

        }, Duration.ofMillis(100), Duration.ofSeconds(5));
        assertThat(TemplateListener.getEmits(tenant).stream().filter(r -> r.getType() == CrudEventType.CREATE).count()).isEqualTo(1L);
        assertThat(TemplateListener.getEmits(tenant).stream().filter(r -> r.getType() == CrudEventType.DELETE).count()).isEqualTo(1L);
    }

    @Singleton
    public static class TemplateListener implements ApplicationEventListener<CrudEvent<Template>> {
        private static List<CrudEvent<Template>> emits = new CopyOnWriteArrayList<>();

        @Override
        public void onApplicationEvent(CrudEvent<Template> event) {
            //The instanceOf is required because Micronaut may send non Template event via this method
            if ((event.getModel() != null && event.getModel() instanceof Template) ||
                    (event.getPreviousModel() != null && event.getPreviousModel() instanceof Template)) {
                emits.add(event);
            }
        }

        public static List<CrudEvent<Template>> getEmits(String tenantId){
            return emits.stream()
                .filter(e -> (e.getModel() != null && e.getModel().getTenantId().equals(tenantId)) ||
                    (e.getPreviousModel() != null && e.getPreviousModel().getTenantId().equals(tenantId)))
                .toList();
        }

        public static void reset() {
            emits = new CopyOnWriteArrayList<>();
        }
    }
}
