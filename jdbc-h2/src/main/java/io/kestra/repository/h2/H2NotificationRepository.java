package io.kestra.repository.h2;

import io.kestra.core.notification.model.Notification;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcNotificationRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2NotificationRepository extends AbstractJdbcNotificationRepository {
    @Inject
    public H2NotificationRepository(@Named("notifications") H2Repository<Notification> repository) {
        super(repository);
    }
}
