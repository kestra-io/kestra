package io.kestra.core.server;

import io.kestra.core.notification.model.AsyncOperationType;

public enum CoreAsyncOperationType implements AsyncOperationType {
    EXECUTION_KILL(ResourceType.EXECUTION),
    EXECUTION_PAUSE(ResourceType.EXECUTION),
    EXECUTION_RESUME(ResourceType.EXECUTION),
    EXECUTION_RESTART(ResourceType.EXECUTION),
    EXECUTION_REPLAY(ResourceType.EXECUTION),
    EXECUTION_FORCE_RUN(ResourceType.EXECUTION),
    EXECUTION_UNQUEUE(ResourceType.EXECUTION),
    EXECUTION_CHANGE_STATUS(ResourceType.EXECUTION),
    EXECUTION_SET_LABELS(ResourceType.EXECUTION),
    TRIGGER_UNLOCK(ResourceType.TRIGGER),
    TRIGGER_DELETE(ResourceType.TRIGGER),
    TRIGGER_DISABLE(ResourceType.TRIGGER),
    TRIGGER_ENABLE(ResourceType.TRIGGER),
    BACKFILL_PAUSE(ResourceType.TRIGGER),
    BACKFILL_RESUME(ResourceType.TRIGGER),
    BACKFILL_DELETE(ResourceType.TRIGGER),
    ;

    private final ResourceType resourceType;

    CoreAsyncOperationType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    @Override
    public ResourceType resourceType() {
        return resourceType;
    }
}
