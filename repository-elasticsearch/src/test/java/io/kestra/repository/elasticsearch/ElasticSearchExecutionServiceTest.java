package io.kestra.repository.elasticsearch;

import io.kestra.core.repositories.AbstractExecutionServiceTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;

class ElasticSearchExecutionServiceTest extends AbstractExecutionServiceTest {
    @Inject
    ElasticSearchRepositoryTestUtils utils;

    @Inject
    ElasticSearchExecutionRepository elasticExecutionRepository;

    @AfterEach
    protected void tearDown() throws IOException {
        utils.tearDown();
        elasticExecutionRepository.initMapping();
    }
}