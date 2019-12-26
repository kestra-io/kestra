package org.kestra.repository.elasticsearch;

import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;
import org.junit.jupiter.api.AfterEach;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;

@Singleton
class ElasticSearchRepositoryTestUtils {

    @Inject
    RestHighLevelClient client;

    @Inject
    List<IndicesConfig> indicesConfigs;

    @Inject
    ElasticSearchFlowRepository elasticSearchFlowRepository;

    @AfterEach
    public void tearDown() throws IOException {
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indicesConfigs.stream()
            .map(IndicesConfig::getName)
            .toArray(String[]::new))
            .indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        client.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);

        elasticSearchFlowRepository.initMapping();
    }
}