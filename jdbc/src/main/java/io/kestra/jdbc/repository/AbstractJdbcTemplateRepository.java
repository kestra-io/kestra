package io.kestra.jdbc.repository;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.templates.Template;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.validation.ConstraintViolationException;

@Singleton
public abstract class AbstractJdbcTemplateRepository extends AbstractJdbcRepository implements TemplateRepositoryInterface {
    private final QueueInterface<Template> templateQueue;
    private final ApplicationEventPublisher<CrudEvent<Template>> eventPublisher;
    protected io.kestra.jdbc.AbstractJdbcRepository<Template> jdbcRepository;

    @SuppressWarnings("unchecked")
    public AbstractJdbcTemplateRepository(io.kestra.jdbc.AbstractJdbcRepository<Template> jdbcRepository, ApplicationContext applicationContext) {
        this.jdbcRepository = jdbcRepository;
        this.eventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);
        this.templateQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.TEMPLATE_NAMED));
    }

    @Override
    public Optional<Template> findById(String namespace, String id) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                Select<Record1<Object>> from = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter())
                    .and(field("namespace").eq(namespace))
                    .and(field("id").eq(id));

                return this.jdbcRepository.fetchOne(from);
            });
    }

    @Override
    public List<Template> findAll() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter());

                return this.jdbcRepository.fetch(select);
            });
    }

    abstract protected Condition findCondition(String query);

    public ArrayListTotal<Template> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace
    ) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(
                        field("value")
                    )
                    .hint(configuration.dialect() == SQLDialect.MYSQL ? "SQL_CALC_FOUND_ROWS" : null)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter());

                if (query != null) {
                    select.and(this.findCondition(query));
                }

                if (namespace != null) {
                    select.and(field("namespace").likeIgnoreCase(namespace + "%"));
                }

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    public List<Template> find(@Nullable String query, @Nullable String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(
                        field("value")
                    )
                    .hint(configuration.dialect() == SQLDialect.MYSQL ? "SQL_CALC_FOUND_ROWS" : null)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter());

                if (query != null) {
                    select.and(this.findCondition(query));
                }

                if (namespace != null) {
                    select.and(field("namespace").likeIgnoreCase(namespace + "%"));
                }

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public List<Template> findByNamespace(String namespace) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(field("namespace").eq(namespace))
                    .and(this.defaultFilter());

                return this.jdbcRepository.fetch(select);
            });
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
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .select(field("namespace"))
                .from(this.jdbcRepository.getTable())
                .where(this.defaultFilter())
                .groupBy(field("namespace"))
                .fetch()
                .map(record -> record.getValue("namespace", String.class))
            );
    }
}
