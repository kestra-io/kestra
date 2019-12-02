package org.floworc.repository.elasticsearch;

import io.micronaut.test.annotation.MicronautTest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.floworc.core.repositories.AbstractFlowRepositoryTest;
import org.floworc.repository.elasticsearch.configs.IndicesConfig;
import org.junit.jupiter.api.AfterEach;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;

@MicronautTest
class ElasticSearchFlowRepositoryTest extends AbstractFlowRepositoryTest {
    @Inject
    RestHighLevelClient client;

    @Inject
    List<IndicesConfig> indicesConfigs;

    @Inject
    ElasticSearchFlowRepository elasticSearchFlowRepository;

    @AfterEach
    protected void tearDown() throws IOException {
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indicesConfigs.stream()
            .map(IndicesConfig::getName)
            .toArray(String[]::new))
            .indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        client.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);

        elasticSearchFlowRepository.initMapping();
    }
}