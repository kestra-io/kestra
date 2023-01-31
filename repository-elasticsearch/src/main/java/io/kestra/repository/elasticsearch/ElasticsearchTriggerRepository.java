package io.kestra.repository.elasticsearch;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.validations.ModelValidator;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.utils.ExecutorsUtils;

import java.util.List;
import java.util.Optional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticsearchTriggerRepository extends AbstractElasticSearchRepository<Trigger> implements TriggerRepositoryInterface {
    private static final String INDEX_NAME = "triggers";

    @Inject
    public ElasticsearchTriggerRepository(
        RestHighLevelClient client,
        ElasticSearchIndicesService elasticSearchIndicesService,
        ExecutorsUtils executorsUtils
    ) {
        super(client, elasticSearchIndicesService, executorsUtils, Trigger.class);
    }

    public Optional<Trigger> findLast(TriggerContext trigger) {
        return this.rawGetRequest(INDEX_NAME, trigger.uid());
    }

    @Override
    public Optional<Trigger> findByExecution(Execution execution) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.termQuery("executionId", execution.getId()))
            .size(1);

        List<Trigger> query = this.query(INDEX_NAME, sourceBuilder);

        return query.size() > 0 ? Optional.of(query.get(0)) : Optional.empty();
    }

    @Override
    public List<Trigger> findAll() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchAllQuery());

        return this.scroll(INDEX_NAME, sourceBuilder);
    }

    @Override
    @VisibleForTesting
    public Trigger save(Trigger trigger) {
        this.putRequest(INDEX_NAME, trigger.uid(), trigger);

        return trigger;
    }
}
