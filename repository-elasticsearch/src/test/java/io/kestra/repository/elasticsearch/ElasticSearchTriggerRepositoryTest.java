package io.kestra.repository.elasticsearch;

import io.kestra.core.repositories.AbstractTriggerRepositoryTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;

class ElasticSearchTriggerRepositoryTest extends AbstractTriggerRepositoryTest {
    @Inject
    ElasticsearchTriggerRepository elasticsearchTriggerRepository;

    @Inject
    private ElasticSearchRepositoryTestUtils utils;

    @AfterEach
    protected void tearDown() throws IOException {
        utils.tearDown();
        elasticsearchTriggerRepository.initMapping();
    }
}
