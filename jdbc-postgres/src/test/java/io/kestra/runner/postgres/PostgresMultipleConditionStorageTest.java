package io.kestra.runner.postgres;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.multipleflows.AbstractMultipleConditionStorageTest;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.jdbc.JdbcTestUtils;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

class PostgresMultipleConditionStorageTest extends AbstractMultipleConditionStorageTest {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    protected MultipleConditionStorageInterface multipleConditionStorage() {
        return new PostgresMultipleConditionStorage(applicationContext);
    }

    protected void save(MultipleConditionStorageInterface multipleConditionStorage, Flow flow, List<MultipleConditionWindow> multipleConditionWindows) {
        ((PostgresMultipleConditionStorage) multipleConditionStorage).save(multipleConditionWindows);
    }


    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}