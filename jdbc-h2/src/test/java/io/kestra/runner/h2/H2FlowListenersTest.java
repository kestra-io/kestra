package io.kestra.runner.h2;

import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.runners.FlowListenersTest;
import io.kestra.core.services.PluginDefaultService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class H2FlowListenersTest extends FlowListenersTest {

    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    QueueInterface<FlowInterface> flowQueue;

    @Inject
    PluginDefaultService pluginDefaultService;

    @Test
    public void all() throws TimeoutException {
        // we don't inject FlowListeners to remove a flaky test
        this.suite(new FlowListeners(flowRepository, flowQueue, pluginDefaultService));
    }
}