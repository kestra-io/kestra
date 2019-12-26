package org.kestra.repository.elasticsearch;

import com.devskiller.friendly_id.FriendlyId;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.annotation.MicronautTest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.repositories.AbstractFlowRepositoryTest;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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

    @Test
    void find() {
        Flow flow1 = Flow.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace("org.kestra.unittest.flow.find")
            .build();
        elasticSearchFlowRepository.save(flow1);
        Flow flow2 = Flow.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace("org.kestra.unittest.flow.find")
            .build();
        elasticSearchFlowRepository.save(flow2);

        ArrayListTotal<Flow> result = elasticSearchFlowRepository.findByNamespace("org.kestra.unittest.flow.find", Pageable.from(1, 5));
        assertThat(result.size(), is(2));
        result = elasticSearchFlowRepository.findByNamespace("org.kestra.unittest.flow.find", Pageable.from(1, 1));
        assertThat(result.size(), is(1));
        result = elasticSearchFlowRepository.findByNamespace("org.kestra.unittest.flow.find", Pageable.from(2, 1));
        assertThat(result.size(), is(1));
    }

}