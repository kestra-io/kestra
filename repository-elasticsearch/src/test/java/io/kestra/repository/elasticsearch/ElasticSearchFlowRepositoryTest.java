package io.kestra.repository.elasticsearch;

import io.kestra.core.Helpers;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.AbstractFlowRepositoryTest;
import io.kestra.repository.elasticsearch.configs.IndicesConfig;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
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

    @Test
    void findSourceCode() {
        List<SearchResult<Flow>> search = flowRepository.findSourceCode("*types.MultipleCondition*", Pageable.from(1, 10, Sort.UNSORTED));

        assertThat((long) search.size(), is(1L));
        assertThat(search.get(0).getModel().getId(), is("trigger-multiplecondition-listener"));
        assertThat(search.get(0).getFragments().get(0), containsString("types.MultipleCondition</mark>"));
    }

    @AfterEach
    protected void tearDown() throws IOException {
        utils.tearDown();
        elasticSearchFlowRepository.initMapping();
        elasticSearchTemplateRepository.initMapping();
    }
}
