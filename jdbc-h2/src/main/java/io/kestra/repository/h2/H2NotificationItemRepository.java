package io.kestra.repository.h2;

import io.kestra.core.notification.model.NotificationItem;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcNotificationItemRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2NotificationItemRepository extends AbstractJdbcNotificationItemRepository {
    @Inject
    public H2NotificationItemRepository(@Named("notification_items") H2Repository<NotificationItem> repository) {
        super(repository);
    }
}
