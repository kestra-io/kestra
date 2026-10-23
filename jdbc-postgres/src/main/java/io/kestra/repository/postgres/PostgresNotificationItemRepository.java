package io.kestra.repository.postgres;

import io.kestra.core.notification.model.NotificationItem;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcNotificationItemRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresNotificationItemRepository extends AbstractJdbcNotificationItemRepository {
    @Inject
    public PostgresNotificationItemRepository(@Named("notification_items") final PostgresRepository<NotificationItem> repository) {
        super(repository);
    }
}
