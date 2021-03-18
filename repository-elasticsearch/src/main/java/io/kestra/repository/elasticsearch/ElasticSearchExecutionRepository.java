package io.kestra.repository.elasticsearch;

import io.micronaut.data.model.Pageable;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.LongBounds;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.ParsedStats;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.utils.ExecutorsUtils;

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
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate
    ) {
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }

        if (endDate == null) {
            endDate = LocalDate.now();
        }

        TermsAggregationBuilder agg = AggregationBuilders.terms(NAMESPACE_AGG)
            .size(10000)
            .field("namespace")
            .subAggregation(
                AggregationBuilders.terms(FLOW_AGG)
                    .size(10000)
                    .field("flowId")
                    .subAggregation(dailyExecutionStatisticsFinalAgg(startDate, endDate, false))
            );

        SearchSourceBuilder sourceBuilder = this.searchSource(
            this.dailyExecutionStatisticsBool(query, startDate, endDate),
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
                    final String namespace = namespaceBucket.getKeyAsString();

                    ((ParsedStringTerms) namespaceBucket.getAggregations().get(FLOW_AGG)).getBuckets()
                        .forEach(flowBucket -> {
                            final String flowId = flowBucket.getKeyAsString();

                            ((ParsedDateHistogram) flowBucket.getAggregations().get(DATE_AGG)).getBuckets()
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
                        });
                });


            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<DailyExecutionStatistics> dailyStatistics(
        @Nullable String query,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        boolean isTaskRun
    ) {
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }

        if (endDate == null) {
            endDate = LocalDate.now();
        }

        AggregationBuilder agg = dailyExecutionStatisticsFinalAgg(startDate, endDate, isTaskRun);

        if (isTaskRun) {
            agg = AggregationBuilders.nested(NESTED_AGG, "taskRunList")
                .subAggregation(agg);
        }

        SearchSourceBuilder sourceBuilder = this.searchSource(
            this.dailyExecutionStatisticsBool(query, startDate, endDate),
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

    private BoolQueryBuilder dailyExecutionStatisticsBool(String query, LocalDate startDate, LocalDate endDate) {
        BoolQueryBuilder bool = this.defaultFilter();

        bool.must(QueryBuilders.rangeQuery("state.startDate")
            .gte(startDate)
        );

        bool.must(QueryBuilders.rangeQuery("state.startDate")
            .lte(endDate)
        );

        if (query != null) {
            bool.must(QueryBuilders.queryStringQuery(query));
        }

        return bool;
    }

    private static DateHistogramAggregationBuilder dailyExecutionStatisticsFinalAgg(
        LocalDate startDate,
        LocalDate endDate,
        boolean isTaskRun
    ) {
        return AggregationBuilders.dateHistogram(DATE_AGG)
            .field((isTaskRun ? "taskRunList." : "") + "state.startDate")
            .format(START_DATE_FORMAT)
            .minDocCount(0)
            .fixedInterval(DateHistogramInterval.DAY)
            .extendedBounds(new LongBounds(
                startDate.format(DateTimeFormatter.ofPattern(START_DATE_FORMAT)),
                endDate.format(DateTimeFormatter.ofPattern(START_DATE_FORMAT))
            ))
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

    @Override
    public ArrayListTotal<Execution> find(String query, Pageable pageable, List<State.Type> state) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.queryStringQuery(query));
        if (state != null) {
            bool = bool.must(QueryBuilders.termsQuery("state.current", state));
        }
        SearchSourceBuilder sourceBuilder = this.searchSource(bool, Optional.empty(), pageable);

        return this.query(INDEX_NAME, sourceBuilder);
    }

    @Override
    public ArrayListTotal<TaskRun> findTaskRun(String query, Pageable pageable, @Nullable List<State.Type> state) {
        BoolQueryBuilder filterAggQuery = QueryBuilders
            .boolQuery()
            .filter(QueryBuilders.queryStringQuery(query));

        if (state != null) {
            filterAggQuery = filterAggQuery.must(QueryBuilders.termsQuery("taskRunList.state.current", state));
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
            .filter(
                QueryBuilders.nestedQuery("taskRunList", QueryBuilders.queryStringQuery(query), ScoreMode.Total)
            );
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
                        return mapper.readValue(documentFields.getSourceAsString(), TaskRun.class);
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
