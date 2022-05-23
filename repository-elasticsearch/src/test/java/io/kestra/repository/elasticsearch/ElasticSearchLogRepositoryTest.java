package io.kestra.repository.elasticsearch;

import io.kestra.core.repositories.AbstractLogRepositoryTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;

class ElasticSearchLogRepositoryTest extends AbstractLogRepositoryTest {
    @Inject
    ElasticSearchLogRepository elasticSearchLogRepository;

    @Inject
    private ElasticSearchRepositoryTestUtils utils;

    @AfterEach
    protected void tearDown() throws IOException {
        utils.tearDown();
        elasticSearchLogRepository.initMapping();
    }
}
