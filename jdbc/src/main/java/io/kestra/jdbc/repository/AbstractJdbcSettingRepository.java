package io.kestra.jdbc.repository;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.Setting;
import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import lombok.SneakyThrows;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractJdbcSettingRepository extends AbstractJdbcCrudRepository<Setting> implements SettingRepositoryInterface {
    private final ApplicationEventPublisher<CrudEvent<Setting>> eventPublisher;

    @SuppressWarnings("unchecked")
    public AbstractJdbcSettingRepository(
        io.kestra.jdbc.AbstractJdbcRepository<Setting> jdbcRepository,
        QueueService queueService,
        ApplicationContext applicationContext
    ) {
        super(jdbcRepository, queueService);
        this.eventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);
    }

    public Boolean isTaskRunEnabled() {
        return false;
    }

    @Override
    public Optional<Setting> findByKey(String key) {
        return findOne(DSL.trueCondition(), field("key").eq(key));
    }

    @Override
    public List<Setting> findAll() {
        return findAll(DSL.trueCondition());
    }

    @Override
    public Setting save(Setting setting) {
        this.eventPublisher.publishEvent(new CrudEvent<>(setting, CrudEventType.UPDATE));

        return internalSave(setting);
    }

    @Override
    public Setting internalSave(Setting setting) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(setting);
        this.jdbcRepository.persist(setting, fields);

        return setting;
    }

    @SneakyThrows
    @Override
    public Setting delete(Setting setting) {
        Optional<Setting> get = this.findByKey(setting.getKey());
        if (get.isEmpty()) {
            throw new IllegalStateException("Setting " + setting.getKey() + " doesn't exists");
        }

        this.jdbcRepository.delete(setting);
        this.eventPublisher.publishEvent(CrudEvent.delete(setting));

        return setting;
    }

    @Override
    protected Condition defaultFilter(String tenantId) {
        return buildTenantCondition(tenantId);
    }

    @Override
    protected Condition defaultFilter() {
        return DSL.trueCondition();
    }
}
