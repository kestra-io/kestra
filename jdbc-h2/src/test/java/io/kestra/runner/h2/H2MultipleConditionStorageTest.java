package io.kestra.runner.h2;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.multipleflows.AbstractMultipleConditionStorageTest;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.repository.h2.H2Repository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

class H2MultipleConditionStorageTest extends AbstractMultipleConditionStorageTest {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    @Named("multipleconditions")
    H2Repository<MultipleConditionWindow> repository;

    protected MultipleConditionStorageInterface multipleConditionStorage() {
        return new H2MultipleConditionStorage(repository);
    }

    protected void save(MultipleConditionStorageInterface multipleConditionStorage, Flow flow, List<MultipleConditionWindow> multipleConditionWindows) {
        ((H2MultipleConditionStorage) multipleConditionStorage).save(multipleConditionWindows);
    }


    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}