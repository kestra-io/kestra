package org.kestra.repository.elasticsearch;

import io.micronaut.data.model.Pageable;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.composite.*;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.metrics.ParsedStats;
import org.elasticsearch.search.aggregations.metrics.StatsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.metrics.ExecutionMetrics;
import org.kestra.core.models.executions.metrics.ExecutionMetricsAggregation;
import org.kestra.core.models.executions.metrics.Stats;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.validations.ModelValidator;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.ExecutionRepositoryInterface;
import org.kestra.core.utils.ThreadMainFactoryBuilder;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;


@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticSearchExecutionRepository extends AbstractElasticSearchRepository<Execution> implements ExecutionRepositoryInterface {
    private static final String INDEX_NAME = "executions";
    public static final String START_DATE_FORMAT = "yyyy-MM-dd";
    public static final int MAX_BUCKET_SIZE = 1000;

    @Inject
    public ElasticSearchExecutionRepository(
        RestHighLevelClient client,
        List<IndicesConfig> indicesConfigs,
        ModelValidator modelValidator,
        ThreadMainFactoryBuilder threadFactoryBuilder
    ) {
        super(client, indicesConfigs, modelValidator, threadFactoryBuilder, Execution.class);
    }

    @Override
    public Optional<Execution> findById(String id) {
        return this.getRequest(INDEX_NAME, id);
    }

    public Map<String, ExecutionMetricsAggregation> aggregateByStateWithDurationStats(String query, Pageable pageable) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.queryStringQuery(query));

        final List<CompositeValuesSourceBuilder<?>> multipleFieldsSources = new ArrayList<>();
        multipleFieldsSources.add(new TermsValuesSourceBuilder("namespace").field("namespace"));
        multipleFieldsSources.add(new TermsValuesSourceBuilder("flowId").field("flowId"));
        multipleFieldsSources.add(new TermsValuesSourceBuilder("state.current").field("state.current"));
        multipleFieldsSources.add(new DateHistogramValuesSourceBuilder("state.startDate").field("state.startDate").fixedInterval(DateHistogramInterval.DAY).format(START_DATE_FORMAT));

        CompositeAggregationBuilder groupByMultipleFieldsAggBuilder = AggregationBuilders.composite(
            "group_by_multiple_fields", multipleFieldsSources).size(MAX_BUCKET_SIZE);

        final List<CompositeValuesSourceBuilder<?>> durationSources = new ArrayList<>();
        durationSources.add(new TermsValuesSourceBuilder("namespace").field("namespace"));
        durationSources.add(new TermsValuesSourceBuilder("flowId").field("flowId"));

        CompositeAggregationBuilder durationAggBuilder =
            AggregationBuilders.composite("duration", durationSources).subAggregation(new StatsAggregationBuilder(
                "state.duration").field("state.duration")).size(MAX_BUCKET_SIZE);


        SearchSourceBuilder sourceBuilder = this.searchSource(bool,
            Optional.of(List.of(groupByMultipleFieldsAggBuilder, durationAggBuilder)),
            pageable);

        try {
            SearchRequest searchRequest = searchRequest(INDEX_NAME, sourceBuilder, false);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            ParsedComposite multipleFieldsParsedComposite = searchResponse.getAggregations().get(
                "group_by_multiple_fields");
            ParsedComposite durationParsedComposite = searchResponse.getAggregations().get("duration");

            Map<String, ExecutionMetricsAggregation> map = new HashMap<>();

            multipleFieldsParsedComposite.getBuckets().stream().forEach(bucket -> {
                final String namespace = bucket.getKey().get("namespace").toString();
                final String flowId = bucket.getKey().get("flowId").toString();
                final String mapKey = Flow.uniqueIdWithoutRevision(namespace, flowId);

                final State.Type state = State.Type.valueOf(bucket.getKey().get("state.current").toString());

                ExecutionMetricsAggregation executionMetricsAggregation = map.getOrDefault(mapKey,
                    ExecutionMetricsAggregation.builder().namespace(namespace).id(flowId).metrics(new ArrayList<>()).build());

                executionMetricsAggregation.getMetrics().add(ExecutionMetrics.builder().state(state).count(bucket.getDocCount())
                    // TODO : Local date  ?
                    .startDate(LocalDate.parse(bucket.getKey().get("state.startDate").toString(),
                        DateTimeFormatter.ISO_LOCAL_DATE))
                    .build());

                map.put(mapKey, executionMetricsAggregation);
            });

            durationParsedComposite.getBuckets().stream().forEach(bucket -> {
                final String namespace = bucket.getKey().get("namespace").toString();
                final String flowId = bucket.getKey().get("flowId").toString();
                final String mapKey = Flow.uniqueIdWithoutRevision(namespace, flowId);

                ExecutionMetricsAggregation executionMetricsAggregation = map.get(mapKey);

                // Should never occur but since the map is filled from 2 different aggregations, we keep this check
                if (executionMetricsAggregation == null) {
                    return;
                }

                executionMetricsAggregation.setPeriodDurationStats(Stats.builder()
                    .avg(((ParsedStats) bucket.getAggregations().get("state.duration")).getAvg())
                    .build());

            });

            return map;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Stats> findLast24hDurationStats(String query, Pageable pageable) {
        QueryStringQueryBuilder queryString = QueryBuilders.queryStringQuery(query);


        final List<CompositeValuesSourceBuilder<?>> durationSources = new ArrayList<>();
        durationSources.add(new TermsValuesSourceBuilder("namespace").field("namespace"));
        durationSources.add(new TermsValuesSourceBuilder("flowId").field("flowId"));

        final String durationAggKey = "duration";

        CompositeAggregationBuilder durationAggBuilder =
            AggregationBuilders.composite(durationAggKey, durationSources).subAggregation(new StatsAggregationBuilder(
                "state.duration").field("state.duration"));

        SearchSourceBuilder sourceBuilder = this.searchSource(queryString, Optional.of(List.of(durationAggBuilder)),
            pageable);

        try {
            SearchRequest searchRequest = searchRequest(INDEX_NAME, sourceBuilder, false);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            ParsedComposite durationParsedComposite = searchResponse.getAggregations().get(durationAggKey);

            Map<String, Stats> map = new HashMap<>();

            durationParsedComposite.getBuckets().stream().forEach(bucket -> {
                final String namespace = bucket.getKey().get("namespace").toString();
                final String flowId = bucket.getKey().get("flowId").toString();
                final String mapKey = Flow.uniqueIdWithoutRevision(namespace, flowId);

                map.put(mapKey, Stats.builder()
                    .avg(((ParsedStats) bucket.getAggregations().get("state.duration")).getAvg())
                    .build()
                );
            });

            return map;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public ArrayListTotal<Execution> find(String query, Pageable pageable, @Nullable State.Type state) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.queryStringQuery(query));
        if (state != null) {
            bool = bool.must(QueryBuilders.termQuery("state.current", state.name()));
        }
        SearchSourceBuilder sourceBuilder = this.searchSource(bool, Optional.empty(), pageable);

        return this.query(INDEX_NAME, sourceBuilder);
    }

    @Override
    public ArrayListTotal<Execution> findByFlowId(String namespace, String id, Pageable pageable) {
        BoolQueryBuilder bool = QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery("namespace", namespace))
            .must(QueryBuilders.termQuery("flowId", id));

        SearchSourceBuilder sourceBuilder = this.searchSource(bool, Optional.empty(), pageable);

        return this.query(INDEX_NAME, sourceBuilder);
    }

    @Override
    public Execution save(Execution execution) {
        this.putRequest(INDEX_NAME, execution.getId(), execution);

        return execution;
    }
}
