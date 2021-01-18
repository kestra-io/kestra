package org.kestra.repository.memory;

import io.micronaut.test.annotation.MicronautTest;
import org.kestra.core.repositories.AbstractFlowRepositoryTest;

import javax.inject.Inject;

@MicronautTest
public class MemoryFlowRepositoryTest extends AbstractFlowRepositoryTest {

    @Inject
    MemoryFlowRepository memoryFlowRepository;
}
