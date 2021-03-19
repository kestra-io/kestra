package io.kestra.repository.memory;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.kestra.core.repositories.AbstractFlowRepositoryTest;

import javax.inject.Inject;

@MicronautTest
public class MemoryFlowRepositoryTest extends AbstractFlowRepositoryTest {

    @Inject
    MemoryFlowRepository memoryFlowRepository;
}
