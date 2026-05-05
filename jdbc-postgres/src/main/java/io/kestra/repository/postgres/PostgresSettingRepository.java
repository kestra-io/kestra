package io.kestra.repository.postgres;

import io.kestra.core.models.Setting;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.events.CrudEvent;
import io.kestra.jdbc.repository.AbstractJdbcSettingRepository;

import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresSettingRepository extends AbstractJdbcSettingRepository {
    @Inject
    public PostgresSettingRepository(@Named("settings") PostgresRepository<Setting> repository,
         ApplicationEventPublisher<CrudEvent<Setting>> eventPublisher) {
        super(repository, eventPublisher);
    }
}
