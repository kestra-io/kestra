package io.kestra.core.repositories.log;

import io.kestra.core.models.AccessScope;

import jakarta.inject.Singleton;

/**
 * Default OSS {@link LogDataStoreAccessControl}: grants global access to logs and audits nothing.
 * EE replaces this bean with a {@code CurrentUserContext}-based implementation.
 */
@Singleton
public class GlobalLogDataStoreAccessControl implements LogDataStoreAccessControl {

    @Override
    public AccessScope namespaceScope() {
        return AccessScope.global();
    }
}
