package io.kestra.repository.mysql;

import io.kestra.core.models.Setting;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.jdbc.repository.AbstractJdbcSettingRepository;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlSettingRepository extends AbstractJdbcSettingRepository {
    @Inject
    public MysqlSettingRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(Setting.class, applicationContext), applicationContext);
    }}
