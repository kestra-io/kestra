package io.kestra.repository.elasticsearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.repository.elasticsearch.configs.IndicesConfig;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.ShardOperationFailedException;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.micronaut.core.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.inject.Inject;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Slf4j
abstract public class AbstractElasticSearchRepository<T> {
    protected static final ObjectMapper MAPPER = JacksonMapper.ofJson(false);

    private static ExecutorService poolExecutor;

    protected Class<T> cls;
    protected RestHighLevelClient client;
    protected ModelValidator modelValidator;
    protected ElasticSearchIndicesService elasticSearchIndicesService;

    protected Map<String, IndicesConfig> indicesConfigs;

    @Inject
    public AbstractElasticSearchRepository(
        RestHighLevelClient client,
        ElasticSearchIndicesService elasticSearchIndicesService,
        ModelValidator modelValidator,
        ExecutorsUtils executorsUtils,
        Class<T> cls
    ) {
        this.startExecutor(executorsUtils);

        this.client = client;
        this.cls = cls;
        this.modelValidator = modelValidator;
        this.elasticSearchIndicesService = elasticSearchIndicesService;

        this.indicesConfigs = elasticSearchIndicesService.findConfig(cls);
    }

    private synchronized void startExecutor(ExecutorsUtils executorsUtils) {
        if (poolExecutor == null) {
            poolExecutor = executorsUtils.cachedThreadPool("elasticsearch-repository");
        }
    }

    protected BoolQueryBuilder defaultFilter() {
        return QueryBuilders.boolQuery()
            .must(QueryBuilders.matchQuery("deleted", false));
    }

    protected Optional<T> getRequest(String index, String id) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.termQuery("_id", id));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(bool)
            .size(1);

        SearchRequest searchRequest = this.searchRequest(
            index,
            sourceBuilder,
            false
        );

        try {
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            return this.map(searchResponse.getHits().getHits())
                .stream()
                .findFirst();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Optional<T> rawGetRequest(String index, String id) {
        GetRequest getRequest = new GetRequest(
            this.indicesConfigs.get(index).getIndex(),
            id
        );

        try {
            GetResponse getResponse = client.get(getRequest, RequestOptions.DEFAULT);

            if (!getResponse.isExists()) {
                return Optional.empty();
            }

            return Optional.of(this.deserialize(getResponse.getSourceAsString()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static void handleWriteErrors(DocWriteResponse indexResponse) throws Exception {
        ReplicationResponse.ShardInfo shardInfo = indexResponse.getShardInfo();
        if (shardInfo.getTotal() != shardInfo.getSuccessful()) {
            log.warn("Replication incomplete, expected " + shardInfo.getTotal() + ", got " + shardInfo.getSuccessful());
        }

        if (shardInfo.getFailed() > 0) {
            throw new Exception(
                Stream.of(shardInfo.getFailures())
                    .map(ShardOperationFailedException::reason)
                    .collect(Collectors.joining("\n"))
            );
        }
    }

    protected IndexResponse putRequest(String index, String id, String json) {
        IndexRequest request = new IndexRequest(this.indicesConfigs.get(index).getIndex());
        request.id(id);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        request.source(json, XContentType.JSON);

        try {
            IndexResponse response = client.index(request, RequestOptions.DEFAULT);
            handleWriteErrors(response);

            return response;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected IndexResponse putRequest(String index, String id, T source) {
        try {
            String json = MAPPER.writeValueAsString(source);
            return this.putRequest(index, id, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    protected UpdateResponse updateRequest(String index, String id, XContentBuilder doc) {
        UpdateRequest request = new UpdateRequest(this.indicesConfigs.get(index).getIndex(), id);
        request.doc(doc);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        try {
            UpdateResponse response = client.update(request, RequestOptions.DEFAULT);
            handleWriteErrors(response);

            return response;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows
    protected UpdateResponse deleteRequest(String index, String id) {
        XContentBuilder delete = jsonBuilder()
            .startObject()
            .field("deleted", true)
            .endObject();

        return this.updateRequest(index, id, delete);
    }

    protected SearchRequest searchRequest(String index, SearchSourceBuilder sourceBuilder, boolean scroll) {
        SearchRequest searchRequest = new SearchRequest()
            .indices(this.indicesConfigs.get(index).getIndex())
            .source(sourceBuilder);

        if (scroll) {
            searchRequest.scroll(new TimeValue(60000));
        }

        return searchRequest;
    }

    protected Predicate<Sort.Order> isDurationSort() {
        return order -> order != null
            && order.getProperty() != null
            && order.getProperty().contains("state.duration");
    }

    protected SortOrder toSortOrder(Sort.Order.Direction sortDirection) {
        return sortDirection == Sort.Order.Direction.ASC ? SortOrder.ASC : SortOrder.DESC;
    }

    protected SortBuilder<FieldSortBuilder> toFieldSortBuilder(Sort.Order order) {
        return SortBuilders
            .fieldSort(order.getProperty())
            .order(toSortOrder(order.getDirection()));
    }

    public static final String DURATION_SORT_SCRIPT_CODE = "" +
        "ZonedDateTime start = doc[params.fieldPrefix+'state.startDate'].value;\n" +
        "ZonedDateTime end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(params.now), ZoneId.of('Z'));\n" +
        "\n" +
        "if(!params.runningStates.contains(doc[params.fieldPrefix+'state.current'].value) && doc[params.fieldPrefix+'state.endDate'].size() > 0){\n" +
        "  end =  doc[params.fieldPrefix+'state.endDate'].value; \n" +
        "}\n" +
        "return ChronoUnit.MILLIS.between(start,end);";

    protected SortBuilder<ScriptSortBuilder> createDurationSortScript(Sort.Order sortByDuration, boolean nested) {
        return SortBuilders
            .scriptSort(new Script(ScriptType.INLINE,
                    Script.DEFAULT_SCRIPT_LANG,
                    DURATION_SORT_SCRIPT_CODE,
                    Collections.emptyMap(),
                    Map.of("now", new Date().getTime(),
                        "runningStates", Arrays.stream(State.runningTypes()).map(type -> type.name()).toArray(String[]::new),
                        "fieldPrefix", nested ? "taskRunList." : ""
                    )
                ), ScriptSortBuilder.ScriptSortType.NUMBER
            )
            .order(toSortOrder(sortByDuration.getDirection()));
    }

    protected List<SortBuilder<?>> defaultSorts(Pageable pageable, boolean nested) {
        List<SortBuilder<?>> sorts = new ArrayList<>();

        // Use script sort for duration field
        pageable
            .getSort()
            .getOrderBy()
            .stream()
            .filter(isDurationSort())
            .findFirst()
            .ifPresent(order -> sorts.add(createDurationSortScript(order, nested)));

        // Use field sort for all other fields
        sorts.addAll(pageable
            .getSort()
            .getOrderBy()
            .stream()
            .filter(Predicate.not(isDurationSort()))
            .map(this::toFieldSortBuilder)
            .collect(Collectors.toList()));

        return sorts;
    }

    protected SearchSourceBuilder searchSource(
        QueryBuilder query,
        Optional<List<AggregationBuilder>> aggregations,
        @Nullable Pageable pageable
    ) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(query);

        if (pageable != null) {
            sourceBuilder
                .size(pageable.getSize())
                .from(Math.toIntExact(pageable.getOffset() - pageable.getSize()));

            for (SortBuilder<?> s : defaultSorts(pageable, false)) {
                sourceBuilder = sourceBuilder.sort(s);
            }
        } else {
            sourceBuilder.size(0);
        }

        if (aggregations.isPresent()) {
            for (AggregationBuilder aggregation : aggregations.get()) {
                sourceBuilder.aggregation(aggregation);
            }
        }

        return sourceBuilder;
    }

    protected ArrayListTotal<T> findQueryString(String index, String query, Pageable pageable) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.queryStringQuery(query));

        SearchSourceBuilder sourceBuilder = this.searchSource(bool, Optional.empty(), pageable);

        return this.query(index, sourceBuilder);
    }

    protected List<T> searchByIds(String index, List<String> ids) {
        if (ids == null) {
            return new ArrayList<>();
        }

        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.idsQuery()
                .addIds(ids.toArray(String[]::new))
            );

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(bool);

        return this.scroll(index, sourceBuilder);
    }

    protected List<T> map(SearchHit[] searchHits) {
        return Arrays.stream(searchHits)
            .map(searchHit -> this.deserialize(searchHit.getSourceAsString()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    protected T deserialize(String source) {
        try {
            return MAPPER.readValue(source, this.cls);
        } catch (InvalidTypeIdException e) {
            throw new DeserializationException(e);
        } catch (IOException e) {
            throw new DeserializationException(e);
        }
    }

    protected ArrayListTotal<T> query(String index, SearchSourceBuilder sourceBuilder) {
        SearchRequest searchRequest = searchRequest(index, sourceBuilder, false);

        try {
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            return new ArrayListTotal<T>(this.map(searchResponse.getHits().getHits()), searchResponse.getHits().getTotalHits().value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected List<T>  scroll(String index, SearchSourceBuilder sourceBuilder) {
        List<T> result = new ArrayList<>();

        this.scroll(index, sourceBuilder, result::add);

        return result;
    }

    protected void scroll(String index, SearchSourceBuilder sourceBuilder, Consumer<T> consumer) {
        String scrollId = null;
        SearchRequest searchRequest = searchRequest(index, sourceBuilder, true);
        try {
            SearchResponse searchResponse;
            searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            scrollId = searchResponse.getScrollId();

            do {
                this.map(searchResponse.getHits().getHits())
                    .forEach(consumer);

                SearchScrollRequest searchScrollRequest = new SearchScrollRequest()
                    .scrollId(scrollId)
                    .scroll(new TimeValue(60000));

                searchResponse = client.scroll(searchScrollRequest, RequestOptions.DEFAULT);
            } while (searchResponse.getHits().getHits().length != 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            this.clearScrollId(scrollId);
        }
    }

    private void clearScrollId(String scrollId) {
        if (scrollId == null) {
            return;
        }

        poolExecutor.execute(() -> {
            ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            clearScrollRequest.addScrollId(scrollId);

            try {
                client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
            } catch (IOException e) {
                log.warn("Failed to clear scroll", e);
            }
        });
    }

    @PostConstruct
    public void initMapping() {
        this.elasticSearchIndicesService.createIndice(this.indicesConfigs);
        this.elasticSearchIndicesService.updateMapping(this.indicesConfigs);
    }


    protected List<String> findDistinctNamespace(String index) {
        BoolQueryBuilder query = this.defaultFilter();

        // We want to keep only "distinct" values of field "namespace"
        // @TODO: use includeExclude(new IncludeExclude(0, 10)) to partition results
        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders
            .terms("distinct_namespace")
            .field("namespace")
            .size(10000)
            .order(BucketOrder.key(true));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(query)
            .aggregation(termsAggregationBuilder);

        SearchRequest searchRequest = searchRequest(index, sourceBuilder, false);

        try {
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            Terms namespaces = searchResponse.getAggregations().get("distinct_namespace");

            return new ArrayListTotal<>(
                namespaces.getBuckets()
                    .stream()
                    .map(o -> {
                        return o.getKey().toString();
                    })
                    .collect(Collectors.toList()),
                namespaces.getBuckets().size()
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected GetSettingsResponse getSettings(String index, boolean includeDefaults) {
        GetSettingsRequest request = new GetSettingsRequest()
            .indices(this.indicesConfigs.get(index).getIndex())
            .includeDefaults(includeDefaults);
        try {
            return this.client.indices().getSettings(request, RequestOptions.DEFAULT);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }
}
