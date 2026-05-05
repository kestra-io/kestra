package io.kestra.repository.mysql;

import io.kestra.core.models.Setting;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcSettingRepository;
import io.micronaut.context.event.ApplicationEventPublisher;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlSettingRepository extends AbstractJdbcSettingRepository {
    @Inject
    public MysqlSettingRepository(@Named("settings") MysqlRepository<Setting> repository,
          ApplicationEventPublisher<CrudEvent<Setting>> eventPublisher) {
        super(repository, eventPublisher);
    }
}
