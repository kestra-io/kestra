package org.kestra.repository.elasticsearch;

import com.google.common.annotations.VisibleForTesting;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.models.validations.ModelValidator;
import org.kestra.core.repositories.TriggerRepositoryInterface;
import org.kestra.core.utils.ExecutorsUtils;

import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

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
