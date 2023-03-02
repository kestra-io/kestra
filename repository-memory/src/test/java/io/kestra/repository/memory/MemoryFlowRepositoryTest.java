package io.kestra.repository.memory;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.kestra.core.repositories.AbstractFlowRepositoryTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.net.URISyntaxException;

@MicronautTest
public class MemoryFlowRepositoryTest extends AbstractFlowRepositoryTest {

    @Inject
    MemoryFlowRepository memoryFlowRepository;

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        memoryFlowRepository.findAll().forEach(flow -> memoryFlowRepository.delete(flow));
        super.init();
    }
}
