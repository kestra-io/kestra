package io.kestra.repository.memory;

import io.kestra.core.repositories.AbstractFlowRepositoryTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;

public class MemoryFlowRepositoryTest extends AbstractFlowRepositoryTest {

    @Inject
    MemoryFlowRepository memoryFlowRepository;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        memoryFlowRepository.findAllForAllTenants().forEach(flow -> memoryFlowRepository.delete(flow));
        super.init();
    }

    @Test
    void templateDisabled() {

    }
}
