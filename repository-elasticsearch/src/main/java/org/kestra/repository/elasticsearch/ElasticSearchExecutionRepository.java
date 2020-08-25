package org.kestra.repository.elasticsearch;

import io.micronaut.data.model.Pageable;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.DateHistogramValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.ParsedComposite;
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.metrics.ParsedStats;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.validations.ModelValidator;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.ExecutionRepositoryInterface;
import org.kestra.core.utils.ThreadMainFactoryBuilder;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;


@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticSearchExecutionRepository extends AbstractElasticSearchRepository<Execution> implements ExecutionRepositoryInterface {
    private static final String INDEX_NAME = "executions";
    public static final String START_DATE_FORMAT = "yyyy-MM-dd";
    public static final int MAX_BUCKET_SIZE = 1000;

    public static final String GROUP_AGG = "GROUP";
    public static final String COUNT_AGG = "COUNT";
    public static final String DURATION_AGG = "DURATION";

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

    @Override
    public Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(@Nullable String query) {
        SearchSourceBuilder sourceBuilder = dailyExecutionStatisticsSourceBuilder(
            query,
            Arrays.asList(
                new TermsValuesSourceBuilder("namespace").field("namespace"),
                new TermsValuesSourceBuilder("flowId").field("flowId")
            )
        );

        try {
            SearchRequest searchRequest = searchRequest(INDEX_NAME, sourceBuilder, false);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            ParsedComposite groupAgg = searchResponse
                .getAggregations()
                .get(GROUP_AGG);

            Map<String, Map<String, List<DailyExecutionStatistics>>> result = new HashMap<>();

            groupAgg.getBuckets().forEach(bucket -> {
                ParsedStringTerms countAgg = bucket.getAggregations().get(COUNT_AGG);
                ParsedStats durationAgg = bucket.getAggregations().get(DURATION_AGG);

                final String namespace = bucket.getKey().get("namespace").toString();
                final String flowId = bucket.getKey().get("flowId").toString();
                final LocalDate startDate = LocalDate.parse(bucket.getKey().get("state.startDate").toString(),
                    DateTimeFormatter.ISO_LOCAL_DATE);

                result.compute(namespace, (namespaceKey, namespaceMap) -> {
                    if (namespaceMap == null) {
                        namespaceMap = new HashMap<>();
                    }

                    namespaceMap.compute(flowId, (flowKey, flowList) -> {
                        if (flowList == null) {
                            flowList = new ArrayList<>();
                        }

                        flowList.add(dailyExecutionStatisticsMap(countAgg, durationAgg, startDate));

                        return flowList;
                    });

                    return namespaceMap;
                });
            });

            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SearchSourceBuilder dailyExecutionStatisticsSourceBuilder(String query, List<CompositeValuesSourceBuilder<?>> group) {
        BoolQueryBuilder bool = this.defaultFilter();

        if (query != null) {
            bool.must(QueryBuilders.queryStringQuery(query));
        }

        List<CompositeValuesSourceBuilder<?>> compositeValuesSourceBuilders = group != null  ? new ArrayList<>(group) : new ArrayList<>();
        compositeValuesSourceBuilders.add(new DateHistogramValuesSourceBuilder("state.startDate").field("state.startDate")
            .fixedInterval(DateHistogramInterval.DAY)
            .format(START_DATE_FORMAT)
            .missingBucket(true));

        return this.searchSource(
            bool,
            Optional.of(Collections.singletonList(
                AggregationBuilders
                    .composite(GROUP_AGG, compositeValuesSourceBuilders)
                    .subAggregation(AggregationBuilders.stats(DURATION_AGG).
                        field("state.duration")
                    )
                    .subAggregation(AggregationBuilders.terms(COUNT_AGG)
                        .field("state.current")
                    )
                    .size(MAX_BUCKET_SIZE)
            )),
            null
        );
    }

    private static DailyExecutionStatistics dailyExecutionStatisticsMap(ParsedStringTerms countAgg, ParsedStats durationAgg, LocalDate startDate) {
        return DailyExecutionStatistics.builder()
            .startDate(startDate)
            .duration(DailyExecutionStatistics.Duration.builder()
                .avg(durationFromDouble(durationAgg.getAvg()))
                .min(durationFromDouble(durationAgg.getMin()))
                .max(durationFromDouble(durationAgg.getMax()))
                .sum(durationFromDouble(durationAgg.getSum()))
                .count(durationAgg.getCount())
                .build()
            )
            .executionCounts(countAgg.getBuckets()
                .stream()
                .map(item -> DailyExecutionStatistics.ExecutionCount.builder()
                    .count(item.getDocCount())
                    .state(State.Type.valueOf(item.getKeyAsString()))
                    .build()
                )
                .collect(Collectors.toList())
            )
            .build();
    }

    @Override
    public List<DailyExecutionStatistics> dailyStatistics(String query) {
        SearchSourceBuilder sourceBuilder = dailyExecutionStatisticsSourceBuilder(query, null);

        try {
            SearchRequest searchRequest = searchRequest(INDEX_NAME, sourceBuilder, false);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            ParsedComposite groupAgg = searchResponse
                .getAggregations()
                .get(GROUP_AGG);

            List<DailyExecutionStatistics> result = new ArrayList<>();

            groupAgg.getBuckets().forEach(bucket -> {
                ParsedStringTerms countAgg = bucket.getAggregations().get(COUNT_AGG);
                ParsedStats durationAgg = bucket.getAggregations().get(DURATION_AGG);

                final LocalDate startDate = LocalDate.parse(bucket.getKey().get("state.startDate").toString(), DateTimeFormatter.ISO_LOCAL_DATE);

                result.add(dailyExecutionStatisticsMap(countAgg, durationAgg, startDate));

            });

            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Duration durationFromDouble(double val) {
        return Duration.ofMillis(
            (long) (val * 1000)
        );
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
