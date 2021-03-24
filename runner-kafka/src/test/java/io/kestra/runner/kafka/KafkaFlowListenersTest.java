package io.kestra.runner.kafka;

import org.junit.jupiter.api.Test;
import io.kestra.core.runners.FlowListenersTest;

import javax.inject.Inject;

class KafkaFlowListenersTest extends FlowListenersTest {
    @Inject
    KafkaFlowListeners flowListenersService;

    @Test
    public void all() {
        this.suite(flowListenersService);
    }
}