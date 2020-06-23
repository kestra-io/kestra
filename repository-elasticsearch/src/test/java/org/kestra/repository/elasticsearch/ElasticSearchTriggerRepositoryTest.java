package org.kestra.repository.elasticsearch;

import io.micronaut.test.annotation.MicronautTest;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.AfterEach;
import org.kestra.core.repositories.AbstractTriggerRepositoryTest;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;

import java.io.IOException;
import java.util.List;
import javax.inject.Inject;

@MicronautTest
class ElasticSearchTriggerRepositoryTest extends AbstractTriggerRepositoryTest {
    @Inject
    RestHighLevelClient client;

    @Inject
    List<IndicesConfig> indicesConfigs;

    @Inject
    ElasticsearchTriggerRepository elasticSearchFlowRepository;

    @Inject
    private ElasticSearchRepositoryTestUtils utils;

    @AfterEach
    protected void tearDown() throws IOException {
        utils.tearDown();
        elasticSearchFlowRepository.initMapping();
    }
}
