package io.kestra.repository.memory;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.kestra.core.repositories.AbstractTemplateRepositoryTest;

import javax.inject.Inject;

@MicronautTest
public class MemoryTemplateRepositoryTest extends AbstractTemplateRepositoryTest {

    @Inject
    MemoryFlowRepository memoryFlowRepository;
}
