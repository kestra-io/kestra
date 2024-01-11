package io.kestra.jdbc.repository;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public abstract class AbstractJdbcSettingRepository extends AbstractJdbcRepository implements SettingRepositoryInterface {
    protected final io.kestra.jdbc.AbstractJdbcRepository<Setting> jdbcRepository;
    private final ApplicationEventPublisher<CrudEvent<Setting>> eventPublisher;

    @SuppressWarnings("unchecked")
    public AbstractJdbcSettingRepository(
        io.kestra.jdbc.AbstractJdbcRepository<Setting> jdbcRepository,
        ApplicationContext applicationContext
    ) {
        this.jdbcRepository = jdbcRepository;
        this.eventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);
    }

    public Boolean isTaskRunEnabled() {
        return false;
    }

    @Override
    public Optional<Setting> findByKey(String key) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                Select<Record1<Object>> from = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(field("key").eq(key));

                return this.jdbcRepository.fetchOne(from);
            });
    }

    @Override
    public List<Setting> findAll() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectJoinStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable());

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public Setting save(Setting setting) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(setting);
        this.jdbcRepository.persist(setting, fields);

        eventPublisher.publishEvent(new CrudEvent<>(setting, CrudEventType.UPDATE));

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

        eventPublisher.publishEvent(new CrudEvent<>(setting, CrudEventType.DELETE));

        return setting;
    }
}
