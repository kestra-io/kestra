package io.kestra.runner.mysql;

import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.jdbc.runner.AbstractJdbcMultipleConditionStorage;
import io.kestra.repository.mysql.MysqlRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;

@Singleton
@MysqlQueueEnabled
public class MysqlMultipleConditionStorage extends AbstractJdbcMultipleConditionStorage {
    public MysqlMultipleConditionStorage(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(MultipleConditionWindow.class, applicationContext));
    }
}
