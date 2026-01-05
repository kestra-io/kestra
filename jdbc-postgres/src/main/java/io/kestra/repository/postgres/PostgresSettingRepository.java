package io.kestra.repository.postgres;

import io.kestra.core.models.Setting;
import io.kestra.core.queues.QueueService;
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
                                     QueueService queueService,
                                     ApplicationContext applicationContext) {
        super(repository, queueService, applicationContext);
    }
}
