package io.kestra.repository.mysql;

import io.kestra.core.models.Setting;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcSettingRepository;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlSettingRepository extends AbstractJdbcSettingRepository {
    @Inject
    public MysqlSettingRepository(@Named("settings") MysqlRepository<Setting> repository,
        ApplicationContext applicationContext) {
        super(repository, applicationContext);
    }
}
