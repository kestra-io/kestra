package io.kestra.core.models.notifications;

public enum CoreNotificationType implements NotificationType {
    GENERIC,
    ASYNC_OPERATION,
    ;

    @Override
    public String key() {
        return name();
    }
}
