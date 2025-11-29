package io.kestra.repository.h2;

import io.kestra.core.models.Setting;
import io.kestra.jdbc.repository.AbstractJdbcSettingRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2SettingRepository extends AbstractJdbcSettingRepository {
    @Inject
    public H2SettingRepository(@Named("settings") H2Repository<Setting> repository,
                               ApplicationContext applicationContext) {
        super(repository, applicationContext);
    }
}
