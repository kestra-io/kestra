package io.kestra.jdbc.repository;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.templates.Template;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Optional;
import jakarta.annotation.Nullable;
import jakarta.validation.ConstraintViolationException;

public abstract class AbstractJdbcTemplateRepository extends AbstractJdbcCrudRepository<Template> implements TemplateRepositoryInterface {
    private final QueueInterface<Template> templateQueue;
    private final ApplicationEventPublisher<CrudEvent<Template>> eventPublisher;

    @SuppressWarnings("unchecked")
    public AbstractJdbcTemplateRepository(io.kestra.jdbc.AbstractJdbcRepository<Template> jdbcRepository, QueueService queueService, ApplicationContext applicationContext) {
        super(jdbcRepository, queueService);
        this.eventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);
        this.templateQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.TEMPLATE_NAMED));
    }

    @Override
    public Optional<Template> findById(String tenantId, String namespace, String id) {
        var condition = field("namespace").eq(namespace).and(field("id").eq(id));
        return findOne(tenantId, condition);
    }

    @Override
    public List<Template> findAllWithNoAcl(String tenantId) {
        return findAll(this.defaultFilterWithNoACL(tenantId));
    }

    abstract protected Condition findCondition(String query);

    public ArrayListTotal<Template> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace
    ) {
        Condition condition = computeCondition(query, namespace);

        return findPage(pageable, tenantId, condition);
    }

    @Override
    public List<Template> find(@Nullable String query, @Nullable String tenantId, @Nullable String namespace) {
        Condition condition = computeCondition(query, namespace);

        return find(tenantId, condition);
    }

    private Condition computeCondition(@Nullable String query, @Nullable String namespace) {
        Condition condition = DSL.trueCondition();

        if (query != null) {
            condition = condition.and(this.findCondition(query));
        }
        if (namespace != null) {
            condition = condition.and(DSL.or(field("namespace").eq(namespace), field("namespace").likeIgnoreCase(namespace + ".%")));
        }

        return condition;
    }

    @Override
    public List<Template> findByNamespace(String tenantId, String namespace) {
        var condition = field("namespace").eq(namespace);
        return this.find(tenantId, condition);
    }

    @Override
    public Template create(Template template) throws ConstraintViolationException {
        this.jdbcRepository.persist(template);

        try {
            templateQueue.emit(template);
            eventPublisher.publishEvent(CrudEvent.create(template));

            return template;
        } catch (QueueException e) {
            throw new RuntimeException(e);
        }
    }


    public Template update(Template template, Template previous) throws ConstraintViolationException {
        this
            .findById(previous.getTenantId(), previous.getNamespace(), previous.getId())
            .map(current -> current.validateUpdate(template))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .ifPresent(s -> {
                throw s;
            });

        this.jdbcRepository.persist(template);

        try {
            templateQueue.emit(template);
            eventPublisher.publishEvent(new CrudEvent<>(template, CrudEventType.UPDATE));

            return template;
        } catch (QueueException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(Template template) {
        if (this.findById(template.getTenantId(), template.getNamespace(), template.getId()).isEmpty()) {
            throw new IllegalStateException("Template " + template.getId() + " doesn't exists");
        }

        Template deleted = template.toDeleted();

        this.jdbcRepository.persist(deleted);

        try {
            templateQueue.emit(deleted);
            eventPublisher.publishEvent(CrudEvent.delete(deleted));
        } catch (QueueException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> findDistinctNamespace(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .select(field("namespace"))
                .from(this.jdbcRepository.getTable())
                .where(this.defaultFilter(tenantId))
                .groupBy(field("namespace"))
                .fetch()
                .map(record -> record.getValue("namespace", String.class))
            );
    }
}
