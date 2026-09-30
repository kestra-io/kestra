package io.kestra.repository.postgres;

import io.kestra.core.models.notifications.Notification;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcNotificationRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresNotificationRepository extends AbstractJdbcNotificationRepository {
    @Inject
    public PostgresNotificationRepository(@Named("notifications") final PostgresRepository<Notification> repository) {
        super(repository);
    }
}
