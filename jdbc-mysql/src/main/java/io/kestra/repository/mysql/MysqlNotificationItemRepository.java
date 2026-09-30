package io.kestra.repository.mysql;

import io.kestra.core.models.notifications.NotificationItem;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcNotificationItemRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlNotificationItemRepository extends AbstractJdbcNotificationItemRepository {
    @Inject
    public MysqlNotificationItemRepository(@Named("notification_items") final MysqlRepository<NotificationItem> repository) {
        super(repository);
    }
}
