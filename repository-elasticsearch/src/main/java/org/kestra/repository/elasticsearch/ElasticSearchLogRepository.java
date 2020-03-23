package org.kestra.repository.elasticsearch;

import com.devskiller.friendly_id.FriendlyId;
import io.micronaut.data.model.Pageable;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.validations.ModelValidator;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.LogRepositoryInterface;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;

import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticSearchLogRepository extends AbstractElasticSearchRepository<LogEntry> implements LogRepositoryInterface {
    private static final String INDEX_NAME = "logs";

    @Inject
    public ElasticSearchLogRepository(
        RestHighLevelClient client,
        List<IndicesConfig> indicesConfigs,
        ModelValidator modelValidator
    ) {
        super(client, indicesConfigs, modelValidator, LogEntry.class);
    }

    @Override
    public ArrayListTotal<LogEntry> find(String query, Pageable pageable) {
        return super.findQueryString(INDEX_NAME, query, pageable);
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionId(String id, Pageable pageable) {
        BoolQueryBuilder bool = QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery("executionId", id));

        SearchSourceBuilder sourceBuilder = this.searchSource(bool, Optional.empty(), pageable);

        return this.query(INDEX_NAME, sourceBuilder);
    }

    @Override
    public ArrayListTotal<LogEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId, Pageable pageable) {
        BoolQueryBuilder bool = QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery("executionId", executionId))
            .must(QueryBuilders.termQuery("taskRunId", taskRunId));

        SearchSourceBuilder sourceBuilder = this.searchSource(bool, Optional.empty(), pageable);

        return this.query(INDEX_NAME, sourceBuilder);
    }

    @Override
    public LogEntry save(LogEntry log) {
        this.putRequest(INDEX_NAME, FriendlyId.createFriendlyId(), log);

        return log;
    }
}
