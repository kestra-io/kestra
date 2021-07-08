package io.kestra.jdbc.repository;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.templates.Template;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Optional;
import javax.validation.ConstraintViolationException;

@Singleton
public abstract class AbstractTemplateRepository extends AbstractRepository implements TemplateRepositoryInterface {
    private final QueueInterface<Template> templateQueue;
    private final ApplicationEventPublisher<CrudEvent<Template>> eventPublisher;
    protected AbstractJdbcRepository<Template> jdbcRepository;

    @SuppressWarnings("unchecked")
    public AbstractTemplateRepository(AbstractJdbcRepository<Template> jdbcRepository, ApplicationContext applicationContext) {
        this.jdbcRepository = jdbcRepository;
        this.eventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);
        this.templateQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.TEMPLATE_NAMED));
    }

    @Override
    public Optional<Template> findById(String namespace, String id) {
        Select<Record1<Object>> from = jdbcRepository.getDslContext().select(DSL.field("value"))
            .from(this.jdbcRepository.getTable())
            .where(this.defaultFilter())
            .and(DSL.field("namespace").eq(namespace))
            .and(DSL.field("id").eq(id));

        return this.jdbcRepository.fetchOne(from);
    }

    @Override
    public List<Template> findAll() {
        SelectConditionStep<Record1<Object>> select = this.jdbcRepository
            .getDslContext()
            .select(DSL.field("value"))
            .from(this.jdbcRepository.getTable())
            .where(this.defaultFilter());

        return this.jdbcRepository.fetch(select);
    }

    @Override
    public List<Template> findByNamespace(String namespace) {
        SelectConditionStep<Record1<Object>> select = this.jdbcRepository
            .getDslContext()
            .select(DSL.field("value"))
            .from(this.jdbcRepository.getTable())
            .where(DSL.field("namespace").eq(namespace))
            .and(this.defaultFilter());

        return this.jdbcRepository.fetch(select);
    }

    @Override
    public Template create(Template template) throws ConstraintViolationException {
        this.jdbcRepository.persist(template);

        templateQueue.emit(template);
        eventPublisher.publishEvent(new CrudEvent<>(template, CrudEventType.CREATE));

        return template;
    }

    public Template update(Template template, Template previous) throws ConstraintViolationException {
        this
            .findById(previous.getNamespace(), previous.getId())
            .map(current -> current.validateUpdate(template))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .ifPresent(s -> {
                throw s;
            });

        this.jdbcRepository.persist(template);

        templateQueue.emit(template);
        eventPublisher.publishEvent(new CrudEvent<>(template, CrudEventType.UPDATE));

        return template;
    }

    @Override
    public void delete(Template template) {
        if (this.findById(template.getNamespace(), template.getId()).isEmpty()) {
            throw new IllegalStateException("Template " + template.getId() + " doesn't exists");
        }

        Template deleted = template.toDeleted();

        this.jdbcRepository.persist(deleted);

        templateQueue.emit(deleted);
        eventPublisher.publishEvent(new CrudEvent<>(deleted, CrudEventType.DELETE));
    }

    @Override
    public List<String> findDistinctNamespace() {
        return this.jdbcRepository
            .getDslContext()
            .select(DSL.field("namespace"))
            .from(this.jdbcRepository.getTable())
            .where(this.defaultFilter())
            .groupBy(DSL.grouping(DSL.field("namespace")))
            .fetch()
            .map(record -> record.getValue("namespace", String.class));
    }
}
