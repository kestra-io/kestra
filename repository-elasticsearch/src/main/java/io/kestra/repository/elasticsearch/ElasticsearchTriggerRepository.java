package io.kestra.repository.elasticsearch;

import com.google.common.annotations.VisibleForTesting;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.validations.ModelValidator;
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
        ModelValidator modelValidator,
        ExecutorsUtils executorsUtils
    ) {
        super(client, elasticSearchIndicesService, modelValidator, executorsUtils, Trigger.class);
    }

    public Optional<Trigger> findLast(TriggerContext trigger) {
        return this.rawGetRequest(INDEX_NAME, trigger.uid());
    }

    @Override
    public List<Trigger> findAll() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchAllQuery());

        return this.scroll(INDEX_NAME, sourceBuilder);
    }

    @VisibleForTesting
    Trigger save(Trigger trigger) {
        this.putRequest(INDEX_NAME, trigger.uid(), trigger);

        return trigger;
    }
}
