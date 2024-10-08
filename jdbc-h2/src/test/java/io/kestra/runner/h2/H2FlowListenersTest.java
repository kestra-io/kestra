package io.kestra.runner.h2;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.FlowListenersTest;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class H2FlowListenersTest extends FlowListenersTest {
    @Inject
    JooqDSLContextWrapper dslContextWrapper;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    QueueInterface<FlowWithSource> flowQueue;

    @Test
    public void all() {
        // we don't inject FlowListeners to remove a flaky test
        this.suite(new FlowListeners(flowRepository, flowQueue));
    }

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}