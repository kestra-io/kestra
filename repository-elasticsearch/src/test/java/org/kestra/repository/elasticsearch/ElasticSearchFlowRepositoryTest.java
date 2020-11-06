package org.kestra.repository.elasticsearch;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.annotation.MicronautTest;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kestra.core.Helpers;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.repositories.AbstractFlowRepositoryTest;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;

import java.io.IOException;
import java.util.List;
import javax.inject.Inject;

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

    @Inject
    ElasticSearchTemplateRepository elasticSearchTemplateRepository;

    @Inject
    private ElasticSearchRepositoryTestUtils utils;

    @Test
    void find() {
        List<Flow> save = flowRepository.find("*", Pageable.from(1, 100, Sort.UNSORTED));

        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT));
    }

    @AfterEach
    protected void tearDown() throws IOException {
        utils.tearDown();
        elasticSearchFlowRepository.initMapping();
        elasticSearchTemplateRepository.initMapping();
    }
}
