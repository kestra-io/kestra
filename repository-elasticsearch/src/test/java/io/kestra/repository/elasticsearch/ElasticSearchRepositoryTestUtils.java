package io.kestra.repository.elasticsearch;

import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.junit.jupiter.api.AfterEach;
import io.kestra.repository.elasticsearch.configs.IndicesConfig;

import java.io.IOException;
import java.util.List;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ElasticSearchRepositoryTestUtils {
    @Inject
    RestHighLevelClient client;

    @Inject
    List<IndicesConfig> indicesConfigs;

    @AfterEach
    public void tearDown() throws IOException {
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indicesConfigs.stream()
            .map(IndicesConfig::getIndex)
            .toArray(String[]::new))
            .indicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN);
        client.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);
    }
}
