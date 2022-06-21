package io.kestra.repository.elasticsearch;

import io.kestra.core.repositories.AbstractExecutionRepositoryTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;

public class ElasticSearchExecutionRepositoryTest extends AbstractExecutionRepositoryTest {
    @Inject
    ElasticSearchRepositoryTestUtils utils;

    @Inject
    protected ElasticSearchExecutionRepository elasticExecutionRepository;

    @AfterEach
    protected void tearDown() throws IOException {
        utils.tearDown();
        elasticExecutionRepository.initMapping();
    }
}
