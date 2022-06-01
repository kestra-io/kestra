package io.kestra.repository.elasticsearch;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionCount;
import io.kestra.core.models.executions.statistics.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.utils.ExecutorsUtils;
import io.micronaut.data.model.Pageable;
import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.aggregations.AggregationBuilder;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.bucket.filter.Filter;
import org.opensearch.search.aggregations.bucket.filter.FiltersAggregator;
import org.opensearch.search.aggregations.bucket.filter.ParsedFilters;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.opensearch.search.aggregations.bucket.histogram.LongBounds;
import org.opensearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.opensearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.opensearch.search.aggregations.bucket.nested.ParsedNested;
import org.opensearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.opensearch.search.aggregations.metrics.ParsedStats;
import org.opensearch.search.aggregations.metrics.TopHits;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.annotation.Nullable;

@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticSearchExecutionRepository extends AbstractElasticSearchRepository<Execution> implements ExecutionRepositoryInterface {
    private static final String INDEX_NAME = "executions";
    public static final String DATE_FORMAT = "yyyy-MM-dd";

    public static final String NESTED_AGG = "NESTED";
    public static final String DATE_AGG = "DATE";
    public static final String NAMESPACE_AGG = "NAMESPACE";
    public static final String FLOW_AGG = "FLOW";
    public static final String COUNT_AGG = "COUNT";
    public static final String DURATION_AGG = "DURATION";

    @Inject
    public ElasticSearchExecutionRepository(
        RestHighLevelClient client,
        ElasticSearchIndicesService elasticSearchIndicesService,
        ModelValidator modelValidator,
        ExecutorsUtils executorsUtils
    ) {
        super(client, elasticSearchIndicesService, modelValidator, executorsUtils, Execution.class);
    }

    @Override
    public Optional<Execution> findById(String id) {
        return this.getRequest(INDEX_NAME, id);
    }

    @Override
    public Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        boolean groupByNamespaceOnly
    ) {
        if (startDate == null) {
            startDate = ZonedDateTime.now().minusDays(30);
        }

        if (endDate == null) {
            endDate = ZonedDateTime.now();
        }

        TermsAggregationBuilder agg = AggregationBuilders.terms(NAMESPACE_AGG)
            .size(groupByNamespaceOnly ? 25 : 10000)
            .field("namespace")
            .subAggregation(
                groupByNamespaceOnly ?
                    dailyExecutionStatisticsFinalAgg(startDate, endDate, false) :
                    AggregationBuilders.terms(FLOW_AGG)
                        .size(10000)
                        .field("flowId")
                        .subAggregation(dailyExecutionStatisticsFinalAgg(startDate, endDate, false))
            );

        SearchSourceBuilder sourceBuilder = this.searchSource(
            this.filters(query, startDate, endDate, namespace, flowId, null),
            Optional.of(Collections.singletonList(
                agg
            )),
            null
        );

        try {
            SearchRequest searchRequest = searchRequest(INDEX_NAME, sourceBuilder, false);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            Map<String, Map<String, List<DailyExecutionStatistics>>> result = new HashMap<>();

            ((ParsedStringTerms) searchResponse.getAggregations().get(NAMESPACE_AGG)).getBuckets()
                .forEach(namespaceBucket -> {
                    final String currentNamespace = namespaceBucket.getKeyAsString();

                    if (groupByNamespaceOnly) {
                        this.parseDateAgg(namespaceBucket, result, currentNamespace, "*");
                    } else {
                        ((ParsedStringTerms) namespaceBucket.getAggregations().get(FLOW_AGG)).getBuckets()
                            .forEach(flowBucket -> {
                                final String currentFlowId = flowBucket.getKeyAsString();

                                this.parseDateAgg(flowBucket, result, currentNamespace, currentFlowId);
                            });
                    }
            });

            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private <T extends Terms.Bucket> void parseDateAgg(T bucket, Map<String, Map<String, List<DailyExecutionStatistics>>> result, String namespace, String flowId) {
        ((ParsedDateHistogram) bucket.getAggregations().get(DATE_AGG)).getBuckets()
            .forEach(dateBucket -> {
                final LocalDate currentStartDate = LocalDate.parse(
                    dateBucket.getKeyAsString(),
                    DateTimeFormatter.ISO_LOCAL_DATE
                );

                ParsedStringTerms countAgg = dateBucket.getAggregations().get(COUNT_AGG);
                ParsedStats durationAgg = dateBucket.getAggregations().get(DURATION_AGG);

                result.compute(namespace, (namespaceKey, namespaceMap) -> {
                    if (namespaceMap == null) {
                        namespaceMap = new HashMap<>();
                    }

                    namespaceMap.compute(flowId, (flowKey, flowList) -> {
                        if (flowList == null) {
                            flowList = new ArrayList<>();
                        }

                        flowList.add(dailyExecutionStatisticsMap(countAgg, durationAgg, currentStartDate));

                        return flowList;
                    });

                    return namespaceMap;
                });
            });
    }

    @Override
    public List<ExecutionCount> executionCounts(
        List<Flow> flows,
        @javax.annotation.Nullable List<State.Type> states,
        @javax.annotation.Nullable ZonedDateTime startDate,
        @javax.annotation.Nullable ZonedDateTime endDate
    ) {
        if (startDate == null) {
            startDate = ZonedDateTime.now().minusDays(30);
        }

        if (endDate == null) {
            endDate = ZonedDateTime.now();
        }

        SearchSourceBuilder sourceBuilder = this.searchSource(
            this.filters(null, startDate, endDate, null, null, states),
            Optional.of(Collections.singletonList(
                AggregationBuilders.filters(
                    "FILTERS",
                    flows
                        .stream()
                        .map(flow -> new FiltersAggregator.KeyedFilter(
                            flow.getNamespace() + "/" + flow.getFlowId(),
                            QueryBuilders.boolQuery()
                                .must(QueryBuilders.termQuery("namespace", flow.getNamespace()))
                                .must(QueryBuilders.termQuery("flowId", flow.getFlowId()))
                        ))
                        .toArray(FiltersAggregator.KeyedFilter[]::new)
                )
            )),
            null
        );

        try {
            SearchRequest searchRequest = searchRequest(INDEX_NAME, sourceBuilder, false);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            List<ExecutionCount> result = new ArrayList<>();

            ((ParsedFilters) searchResponse.getAggregations().get("FILTERS")).getBuckets()
                .forEach(filtersBuckets -> {
                    final String key = filtersBuckets.getKeyAsString();

                    result.add(new ExecutionCount(
                        key.split("/")[0],
                        key.split("/")[1],
                        filtersBuckets.getDocCount()
                    ));
                });

            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<DailyExecutionStatistics> dailyStatistics(
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        boolean isTaskRun
    ) {
        if (startDate == null) {
            startDate = ZonedDateTime.now().minusDays(30);
        }

        if (endDate == null) {
            endDate = ZonedDateTime.now();
        }

        AggregationBuilder agg = dailyExecutionStatisticsFinalAgg(startDate, endDate, isTaskRun);

        if (isTaskRun) {
            agg = AggregationBuilders.nested(NESTED_AGG, "taskRunList")
                .subAggregation(agg);
        }

        SearchSourceBuilder sourceBuilder = this.searchSource(
            this.filters(query, startDate, endDate, namespace, flowId, null),
            Optional.of(Collections.singletonList(
                agg
            )),
            null
        );

        try {
            SearchRequest searchRequest = searchRequest(INDEX_NAME, sourceBuilder, false);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            ParsedDateHistogram groupAgg = isTaskRun ?
                ((ParsedNested) searchResponse.getAggregations().get(NESTED_AGG)).getAggregations().get(DATE_AGG) :
                searchResponse.getAggregations().get(DATE_AGG);

            List<DailyExecutionStatistics> result = new ArrayList<>();

            groupAgg.getBuckets().forEach(bucket -> {
                ParsedStringTerms countAgg = bucket.getAggregations().get(COUNT_AGG);
                ParsedStats durationAgg = bucket.getAggregations().get(DURATION_AGG);

                final LocalDate currentStartDate = LocalDate.parse(bucket.getKeyAsString(), DateTimeFormatter.ISO_LOCAL_DATE);

                result.add(dailyExecutionStatisticsMap(countAgg, durationAgg, currentStartDate));

            });

            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private BoolQueryBuilder filters(String query, ZonedDateTime startDate, ZonedDateTime endDate, String namespace, String flowId, List<State.Type> state) {
        BoolQueryBuilder bool = this.defaultFilter();

        if (query != null) {
            bool.must(queryString(query).field("*.fulltext"));
        }

        if (flowId != null && namespace != null) {
            bool = bool.must(QueryBuilders.matchQuery("flowId", flowId));
            bool = bool.must(QueryBuilders.matchQuery("namespace", namespace));
        } else if (namespace != null) {
            bool = bool.must(QueryBuilders.prefixQuery("namespace", namespace));
        }

        if (startDate != null) {
            bool.must(QueryBuilders.rangeQuery("state.startDate").gte(startDate));
        }

        if (endDate != null) {
            bool.must(QueryBuilders.rangeQuery("state.startDate").lte(endDate));
        }

        if (state != null) {
            bool = bool.must(QueryBuilders.termsQuery("state.current", stateConvert(state)));
        }

        if (query != null) {
            bool.must(queryString(query).field("*.fulltext"));
        }

        return bool;
    }

    private static DateHistogramAggregationBuilder dailyExecutionStatisticsFinalAgg(
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        boolean isTaskRun
    ) {
        return AggregationBuilders.dateHistogram(DATE_AGG)
            .field((isTaskRun ? "taskRunList." : "") + "state.startDate")
            .format(DATE_FORMAT)
            .minDocCount(0)
            .fixedInterval(DateHistogramInterval.DAY)
            .extendedBounds(new LongBounds(
                startDate.format(DateTimeFormatter.ofPattern(DATE_FORMAT)),
                endDate.format(DateTimeFormatter.ofPattern(DATE_FORMAT))
            ))
            .timeZone(ZoneId.systemDefault())
            .subAggregation(AggregationBuilders.stats(DURATION_AGG).
                field((isTaskRun ? "taskRunList." : "") + "state.duration")
            )
            .subAggregation(AggregationBuilders.terms(COUNT_AGG)
                .size(10000)
                .field((isTaskRun ? "taskRunList." : "") + "state.current")
            );
    }

    private static DailyExecutionStatistics dailyExecutionStatisticsMap(ParsedStringTerms countAgg, ParsedStats durationAgg, LocalDate startDate) {
        DailyExecutionStatistics build = DailyExecutionStatistics.builder()
            .startDate(startDate)
            .duration(DailyExecutionStatistics.Duration.builder()
                .avg(durationFromDouble(durationAgg.getAvg()))
                .min(durationFromDouble(durationAgg.getMin()))
                .max(durationFromDouble(durationAgg.getMax()))
                .sum(durationFromDouble(durationAgg.getSum()))
                .count(durationAgg.getCount())
                .build()
            )
            .build();

        countAgg.getBuckets()
            .forEach(item -> build.getExecutionCounts()
                .compute(
                    State.Type.valueOf(item.getKeyAsString()),
                    (type, current) -> item.getDocCount()
                )
            );

        return build;
    }

    private static Duration durationFromDouble(double val) {
        return Duration.ofMillis(
            (long) (val * 1000)
        );
    }

    private static List<String> stateConvert(List<State.Type> state) {
        return state
            .stream()
            .map(Enum::name)
            .collect(Collectors.toList());
    }

    @Override
    public ArrayListTotal<Execution> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> state
    ) {
        BoolQueryBuilder bool = this.filters(query, startDate, endDate, namespace, flowId, state);

        SearchSourceBuilder sourceBuilder = this.searchSource(bool, Optional.empty(), pageable);

        return this.query(INDEX_NAME, sourceBuilder);
    }

    @Override
    public ArrayListTotal<TaskRun> findTaskRun(
        Pageable pageable,
        @javax.annotation.Nullable String query,
        @javax.annotation.Nullable String namespace,
        @javax.annotation.Nullable String flowId,
        @javax.annotation.Nullable ZonedDateTime startDate,
        @javax.annotation.Nullable ZonedDateTime endDate,
        @Nullable List<State.Type> states
    ) {
        BoolQueryBuilder filterAggQuery = QueryBuilders.boolQuery();

        if (query != null) {
            filterAggQuery.must(QueryBuilders.queryStringQuery(query).field("*.fulltext"));
        }

        if (states != null) {
            filterAggQuery = filterAggQuery.must(QueryBuilders.termsQuery("taskRunList.state.current", stateConvert(states)));
        }

        NestedAggregationBuilder nestedAgg = AggregationBuilders
            .nested("NESTED", "taskRunList")
            .subAggregation(
                AggregationBuilders.filter("FILTER", filterAggQuery)
                    .subAggregation(
                        AggregationBuilders
                            .topHits("TOPHITS")
                            .size(pageable.getSize())
                            .sorts(defaultSorts(pageable, true))
                            .from(Math.toIntExact(pageable.getOffset() - pageable.getSize()))
                    )
            );

        BoolQueryBuilder mainQuery = this.defaultFilter()
            .filter(QueryBuilders.nestedQuery(
                "taskRunList",
                query != null ? QueryBuilders.queryStringQuery(query).field("*.fulltext") : QueryBuilders.matchAllQuery(),
                ScoreMode.Total
            ));

        SearchSourceBuilder sourceBuilder = this.searchSource(mainQuery, Optional.of(List.of(nestedAgg)), null)
            .fetchSource(false);

        SearchRequest searchRequest = searchRequest(INDEX_NAME, sourceBuilder, false);

        try {
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            ParsedNested pn = searchResponse.getAggregations().get("NESTED");
            Filter fa = pn.getAggregations().get("FILTER");
            long docCount = fa.getDocCount();
            TopHits th = fa.getAggregations().get("TOPHITS");

            List<TaskRun> collect = Arrays.stream(th.getHits().getHits())
                .map(documentFields -> {
                    try {
                        return MAPPER.readValue(documentFields.getSourceAsString(), TaskRun.class);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());

            return new ArrayListTotal<>(collect, docCount);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    @Override
    public Integer maxTaskRunSetting() {
        String max = this.getSettings(INDEX_NAME, true)
            .getSetting(
                this.indicesConfigs.get(INDEX_NAME).getIndex(),
                IndexSettings.MAX_INNER_RESULT_WINDOW_SETTING.getKey()
            );

        return Integer
            .valueOf(max == null ? IndexSettings.MAX_INNER_RESULT_WINDOW_SETTING.getDefault(Settings.EMPTY) : Integer.valueOf(max));
    }
}
