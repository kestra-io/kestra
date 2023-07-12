package io.kestra.repository.h2;

import io.kestra.core.models.Setting;
import io.kestra.jdbc.repository.AbstractJdbcSettingRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Primary;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
@Primary
public class H2SettingRepository extends AbstractJdbcSettingRepository {
    @Inject
    public H2SettingRepository(ApplicationContext applicationContext) {
        super(new H2Repository<>(Setting.class, applicationContext), applicationContext);
    }
}
