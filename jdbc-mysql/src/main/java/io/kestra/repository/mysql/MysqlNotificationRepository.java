package io.kestra.repository.mysql;

import io.kestra.core.models.notifications.Notification;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcNotificationRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlNotificationRepository extends AbstractJdbcNotificationRepository {
    @Inject
    public MysqlNotificationRepository(@Named("notifications") final MysqlRepository<Notification> repository) {
        super(repository);
    }
}
