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
import org.kestra.core.models.executions.metrics.Stats;
import org.kestra.core.models.flows.State;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.ExecutionRepositoryInterface;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticSearchExecutionRepository extends AbstractElasticSearchRepository<Execution> implements ExecutionRepositoryInterface {

    private static final String INDEX_NAME = "executions";
    public static final String START_DATE_FORMAT = "yyyy-MM-dd";


    @Inject
    public ElasticSearchExecutionRepository(RestHighLevelClient client, List<IndicesConfig> indicesConfigs) {
        super(client, indicesConfigs, Execution.class);
    }

    @Override
    public Optional<Execution> findById(String id) {
        return this.getRequest(INDEX_NAME, id);
    }

    public ArrayListTotal<ExecutionMetrics> findAndAggregate(String query, Pageable pageable) {
        QueryStringQueryBuilder queryString = QueryBuilders.queryStringQuery(query);

        final List<CompositeValuesSourceBuilder<?>> sources = new ArrayList<>();
        sources.add(new TermsValuesSourceBuilder("namespace").field("namespace"));
        sources.add(new TermsValuesSourceBuilder("flowId").field("flowId"));
        sources.add(new TermsValuesSourceBuilder("state.current").field("state.current"));
        sources.add(new DateHistogramValuesSourceBuilder("state.startDate")
            .field("state.startDate")
            .dateHistogramInterval(DateHistogramInterval.DAY)
            .interval(1)
            .format(START_DATE_FORMAT)
        );

        CompositeAggregationBuilder compositeAggregationBuilder = AggregationBuilders
            .composite("group_by_multiple_fields", sources)
            .subAggregation(
                new StatsAggregationBuilder("state.duration")
                    .field("state.duration")
            );

        SearchSourceBuilder sourceBuilder = this.searchSource(queryString, Optional.of(compositeAggregationBuilder),
            pageable);

        try {
            SearchRequest searchRequest = searchRequest(INDEX_NAME, sourceBuilder, false);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            ParsedComposite parsedComposite = searchResponse.getAggregations().get("group_by_multiple_fields");

            return new ArrayListTotal<>(parsedComposite
                .getBuckets()
                .stream()
                .map(bucket -> ExecutionMetrics.builder()
                    .namespace(bucket.getKey().get("namespace").toString())
                    .flowId(bucket.getKey().get("flowId").toString())
                    .state(State.Type.valueOf(bucket.getKey().get("state.current").toString()))
                    .startDate(LocalDate.parse(bucket.getKey().get("state.startDate").toString(),
                        DateTimeFormatter.ISO_LOCAL_DATE))
                    .count(bucket.getDocCount())
                    .durationStats(Stats.builder()
                        .avg(((ParsedStats) bucket.getAggregations().get("state.duration")).getAvg())
                        .count(((ParsedStats) bucket.getAggregations().get("state.duration")).getCount())
                        .max(((ParsedStats) bucket.getAggregations().get("state.duration")).getMax())
                        .min(((ParsedStats) bucket.getAggregations().get("state.duration")).getMin())
                        .sum(((ParsedStats) bucket.getAggregations().get("state.duration")).getSum())
                        .build()
                    )
                    .build()).collect(Collectors.toList()), parsedComposite.getBuckets().size());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public ArrayListTotal<Execution> find(String query, Pageable pageable) {
        return super.findQueryString(INDEX_NAME, query, pageable);
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
