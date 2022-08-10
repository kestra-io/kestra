package io.kestra.repository.postgres;

import io.kestra.core.models.Setting;
import io.kestra.jdbc.repository.AbstractJdbcSettingRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresSettingRepository extends AbstractJdbcSettingRepository {
    @Inject
    public PostgresSettingRepository(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(Setting.class, applicationContext), applicationContext);
    }
}
