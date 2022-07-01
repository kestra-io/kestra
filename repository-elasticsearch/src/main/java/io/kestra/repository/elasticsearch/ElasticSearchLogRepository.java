package io.kestra.repository.elasticsearch;

import io.micronaut.data.model.Pageable;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.utils.IdUtils;
import org.slf4j.event.Level;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.annotation.Nullable;

@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticSearchLogRepository extends AbstractElasticSearchRepository<LogEntry> implements LogRepositoryInterface {
    private static final String INDEX_NAME = "logs";

    @Inject
    public ElasticSearchLogRepository(
        RestHighLevelClient client,
        ElasticSearchIndicesService elasticSearchIndicesService,
        ModelValidator modelValidator,
        ExecutorsUtils executorsUtils
    ) {
        super(client, elasticSearchIndicesService, modelValidator, executorsUtils, LogEntry.class);
    }

    @Override
    public ArrayListTotal<LogEntry> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable Level minLevel,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate
    ) {
        BoolQueryBuilder bool = this.defaultFilter();

        if (query != null) {
            bool.must(QueryBuilders.queryStringQuery(query).field("*.fulltext"));
        }

        if (namespace != null) {
            bool.must(QueryBuilders.prefixQuery("namespace", namespace));
        }

        if (flowId != null) {
            bool.must(QueryBuilders.termQuery("flowId", flowId));
        }

        if (minLevel != null) {
            bool.must(minLevel(minLevel));
        }

        if (startDate != null) {
            bool.must(QueryBuilders.rangeQuery("timestamp").gte(startDate));
        }

        if (endDate != null) {
            bool.must(QueryBuilders.rangeQuery("timestamp").lte(endDate));
        }

        SearchSourceBuilder sourceBuilder = this.searchSource(bool, Optional.empty(), pageable)
            .sort("timestamp", SortOrder.DESC);

        return this.query(INDEX_NAME, sourceBuilder);
    }

    @Override
    public List<LogEntry> findByExecutionId(String id, Level minLevel) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.termQuery("executionId", id));

        if (minLevel != null) {
            bool.must(minLevel(minLevel));
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(bool)
            .sort("timestamp", SortOrder.ASC);

        return this.scroll(INDEX_NAME, sourceBuilder);
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskId(String executionId, String taskId, Level minLevel) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.termQuery("executionId", executionId))
            .must(QueryBuilders.termQuery("taskId", taskId));

        if (minLevel != null) {
            bool.must(minLevel(minLevel));
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(bool)
            .sort("timestamp", SortOrder.ASC);

        return this.scroll(INDEX_NAME, sourceBuilder);
    }

    @Override
    public List<LogEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId, Level minLevel) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.termQuery("executionId", executionId))
            .must(QueryBuilders.termQuery("taskRunId", taskRunId));

        if (minLevel != null) {
            bool.must(minLevel(minLevel));
        }

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(bool)
            .sort("timestamp", SortOrder.ASC);

        return this.scroll(INDEX_NAME, sourceBuilder);
    }

    @Override
    public LogEntry save(LogEntry log) {
        this.putRequest(INDEX_NAME, IdUtils.create(), log);

        return log;
    }

    private TermsQueryBuilder minLevel(Level minLevel) {
        return QueryBuilders.termsQuery("level", LogEntry.findLevelsByMin(minLevel));
    }
}
