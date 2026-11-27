package io.kestra.jdbc.repository;

import java.util.List;
import java.util.Optional;

import org.jooq.*;
import org.jooq.impl.DSL;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;

import io.micronaut.context.event.ApplicationEventPublisher;
import lombok.SneakyThrows;

public abstract class AbstractJdbcSettingRepository extends AbstractJdbcCrudRepository<Setting> implements SettingRepositoryInterface {
    @SuppressWarnings("unchecked")
    public AbstractJdbcSettingRepository(
        io.kestra.jdbc.AbstractJdbcRepository<Setting> jdbcRepository,
        ApplicationEventPublisher<CrudEvent<Setting>> eventPublisher) {
        super(jdbcRepository, eventPublisher);
    }

    public Boolean isTaskRunEnabled() {
        return false;
    }

    @Override
    public Optional<Setting> findByKey(String key) {
        return findOne(DSL.noCondition(), KEY_FIELD.eq(key));
    }

    @Override
    public List<Setting> findAll() {
        return findAll(DSL.noCondition());
    }

    @Override
    public Setting save(Setting setting) {
        return this.update(setting);
    }

    @Override
    public Setting internalSave(Setting setting) {
        return super.save(setting);
    }

    @SneakyThrows
    @Override
    public Setting delete(Setting setting) {
        Optional<Setting> get = this.findByKey(setting.getKey());
        if (get.isEmpty()) {
            throw new IllegalStateException("Setting " + setting.getKey() + " doesn't exists");
        }

        return super.delete(setting);
    }

    @Override
    protected Condition defaultFilter(String tenantId) {
        return buildTenantCondition(tenantId);
    }

    @Override
    protected Condition defaultFilter() {
        return DSL.noCondition();
    }
}
