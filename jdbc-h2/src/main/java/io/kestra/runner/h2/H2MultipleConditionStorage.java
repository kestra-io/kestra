package io.kestra.runner.h2;

import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.jdbc.runner.AbstractJdbcMultipleConditionStorage;
import io.kestra.repository.h2.H2Repository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2QueueEnabled
public class H2MultipleConditionStorage extends AbstractJdbcMultipleConditionStorage {
    public H2MultipleConditionStorage(@Named("multipleconditions") H2Repository<MultipleConditionWindow> repository) {
        super(repository);
    }
}
