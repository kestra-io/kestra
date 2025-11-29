package io.kestra.repository.postgres;

import io.kestra.core.models.Setting;
import io.kestra.jdbc.repository.AbstractJdbcSettingRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresSettingRepository extends AbstractJdbcSettingRepository {
    @Inject
    public PostgresSettingRepository(@Named("settings") PostgresRepository<Setting> repository,
                                     ApplicationContext applicationContext) {
        super(repository, applicationContext);
    }
}
