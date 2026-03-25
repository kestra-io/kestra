package io.kestra.runner.mysql;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.jooq.Condition;

import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.jdbc.runner.AbstractJdbcMultipleStateStore;
import io.kestra.repository.mysql.MysqlRepository;
import io.kestra.repository.mysql.MysqlRepositoryEnabled;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlMultipleStateStore extends AbstractJdbcMultipleStateStore {
    public MysqlMultipleStateStore(@Named("multipleconditions") MysqlRepository<MultipleConditionWindow> repository) {
        super(repository);
    }

    @Override
    protected Condition getEndDataCondition() {
        return field("end_date").lt(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
