package io.kestra.repository.mysql;

import io.kestra.core.models.Setting;
import io.kestra.core.queues.QueueService;
import io.kestra.jdbc.repository.AbstractJdbcSettingRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlSettingRepository extends AbstractJdbcSettingRepository {
    @Inject
    public MysqlSettingRepository(@Named("settings") MysqlRepository<Setting> repository,
                                  QueueService queueService,
                                  ApplicationContext applicationContext) {
        super(repository, queueService, applicationContext);
    }
}
