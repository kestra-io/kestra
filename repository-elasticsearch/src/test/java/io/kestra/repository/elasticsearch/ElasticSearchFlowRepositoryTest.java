package io.kestra.repository.elasticsearch;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.kestra.core.Helpers;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.AbstractFlowRepositoryTest;
import io.kestra.repository.elasticsearch.configs.IndicesConfig;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class ElasticSearchFlowRepositoryTest extends AbstractFlowRepositoryTest {
    @Inject
    RestHighLevelClient client;

    @Inject
    List<IndicesConfig> indicesConfigs;

    @Inject
    ElasticSearchFlowRepository elasticSearchFlowRepository;

    @Inject
    ElasticSearchTemplateRepository elasticSearchTemplateRepository;

    @Inject
    private ElasticSearchRepositoryTestUtils utils;

    @Test
    void find() {
        List<Flow> save = flowRepository.find("*", Pageable.from(1, 100, Sort.UNSORTED));

        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT));
    }

    @Test
    void computeTasksTypesKeyword() throws IOException {
        var flow = flowRepository.findById("org.kestra.tests", "all-flowable").get();
        String id = String.join("_", Arrays.asList(
            flow.getNamespace(),
            flow.getId()
        ));
        elasticSearchFlowRepository.putRequest("flows", id, flow);
        for (Task task: flow.getTasks()) {
            task.getType();
        }
        GetRequest getRequest = new GetRequest(
            this.indicesConfigs.get(0).getIndex(),
            id
        );
        GetResponse getResponse = client.get(getRequest, RequestOptions.DEFAULT);

        var response = getResponse.getSourceAsMap();

        var resultFlow = elasticSearchFlowRepository.rawGetRequest("flows", id).get();
        int count = 0;
        for (Task t: flow.getTasks()) {
            count++;
            assertThat(t.getType(), in(resultFlow.getTaskIdList()));
        }
        assertThat(count, equalTo(resultFlow.getTaskIdList().size()));
        assertThat(count, greaterThan(0));
    }

    @AfterEach
    protected void tearDown() throws IOException {
        utils.tearDown();
        elasticSearchFlowRepository.initMapping();
        elasticSearchTemplateRepository.initMapping();
    }
}
