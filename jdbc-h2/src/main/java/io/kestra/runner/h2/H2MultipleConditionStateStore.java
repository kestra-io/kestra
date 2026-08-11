package io.kestra.runner.h2;

import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.jdbc.runner.AbstractJdbcMultipleConditionStateStore;
import io.kestra.repository.h2.H2Repository;
import io.kestra.repository.h2.H2RepositoryEnabled;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2MultipleConditionStateStore extends AbstractJdbcMultipleConditionStateStore {
    public H2MultipleConditionStateStore(@Named("multipleconditions") H2Repository<MultipleConditionWindow> repository) {
        super(repository);
    }

}
