package io.kestra.runner.mysql;

import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.jdbc.runner.AbstractJdbcMultipleConditionStorage;
import io.kestra.repository.mysql.MysqlRepository;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jooq.Condition;

@Singleton
@MysqlQueueEnabled
public class MysqlMultipleConditionStorage extends AbstractJdbcMultipleConditionStorage {
    public MysqlMultipleConditionStorage(@Named("multipleconditions") MysqlRepository<MultipleConditionWindow> repository) {
        super(repository);
    }

    @Override
    protected Condition getEndDataCondition(){
        return field("end_date").lt(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
