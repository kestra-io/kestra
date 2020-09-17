package org.kestra.repository.elasticsearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.ShardOperationFailedException;
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
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.kestra.core.models.validations.ModelValidator;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.serializers.JacksonMapper;
import org.kestra.core.utils.ThreadMainFactoryBuilder;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.inject.Inject;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Slf4j
abstract public class AbstractElasticSearchRepository<T> {
    private static ExecutorService poolExecutor;

    protected static final ObjectMapper mapper = JacksonMapper.ofJson();
    protected Class<T> cls;

    protected RestHighLevelClient client;

    protected Map<String, IndicesConfig> indicesConfigs;

    protected ModelValidator modelValidator;

    @Inject
    public AbstractElasticSearchRepository(
        RestHighLevelClient client,
        List<IndicesConfig> indicesConfigs,
        ModelValidator modelValidator,
        ThreadMainFactoryBuilder threadFactoryBuilder,
        Class<T> cls
    ) {
        this.startExecutor(threadFactoryBuilder);

        this.client = client;
        this.cls = cls;
        this.modelValidator = modelValidator;

        this.indicesConfigs = indicesConfigs
            .stream()
            .filter(r -> r.getCls() == this.cls)
            .map(r -> new AbstractMap.SimpleEntry<>(r.getName(), r))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private synchronized void startExecutor(ThreadMainFactoryBuilder threadFactoryBuilder) {
        if (poolExecutor == null) {
            poolExecutor = Executors.newCachedThreadPool(threadFactoryBuilder.build("elasticsearch-repository-%d"));
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

            return Optional.of(mapper.readValue(getResponse.getSourceAsString(), this.cls));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static void handleWriteErrors(DocWriteResponse indexResponse) throws Exception {
        ReplicationResponse.ShardInfo shardInfo = indexResponse.getShardInfo();
        if (shardInfo.getTotal() != shardInfo.getSuccessful()) {
            log.warn("Replication incomplete, expected " + shardInfo.getTotal() + ", got " + shardInfo.getSuccessful()) ;
        }

        if (shardInfo.getFailed() > 0) {
            throw new Exception(
                Stream.of(shardInfo.getFailures())
                    .map(ShardOperationFailedException::reason)
                    .collect(Collectors.joining("\n"))
            );
        }
    }

    protected IndexResponse putRequest(String index, String id, T source) {
        IndexRequest request = new IndexRequest(this.indicesConfigs.get(index).getIndex());
        request.id(id);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        try {
            String json = mapper.writeValueAsString(source);
            request.source(json, XContentType.JSON);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        try {
            IndexResponse response = client.index(request, RequestOptions.DEFAULT);
            handleWriteErrors(response);

            return response;
        } catch (Exception e) {
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

            for (Sort.Order order : pageable.getSort().getOrderBy()) {
                sourceBuilder = sourceBuilder.sort(
                    order.getProperty(),
                    order.getDirection() == Sort.Order.Direction.ASC ? SortOrder.ASC : SortOrder.DESC
                );
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

    private List<T> map(SearchHit[] searchHits) {
        return Arrays.stream(searchHits)
            .map(documentFields -> {
                try {
                    return mapper.readValue(documentFields.getSourceAsString(), this.cls);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toList());
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

    protected List<T> scroll(String index, SearchSourceBuilder sourceBuilder) {
        List<T> result = new ArrayList<>();

        String scrollId = null;
        SearchRequest searchRequest = searchRequest(index, sourceBuilder, true);
        try {
            SearchResponse searchResponse;
            searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            scrollId = searchResponse.getScrollId();

            do {
                result.addAll(this.map(searchResponse.getHits().getHits()));

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

        return result;
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
        this.createIndice();
        this.updateMapping();
    }

    private void createIndice() {
        try {
            for (Map.Entry<String, IndicesConfig> index : this.indicesConfigs.entrySet()) {
                GetIndexRequest exists = new GetIndexRequest(index.getValue().getIndex());
                if (!client.indices().exists(exists, RequestOptions.DEFAULT)) {
                    CreateIndexRequest request = new CreateIndexRequest(index.getValue().getIndex());
                    request.settings(index.getValue().getSettingsContent(), XContentType.JSON);

                    client.indices().create(request, RequestOptions.DEFAULT);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateMapping() {
        for (Map.Entry<String, IndicesConfig> index : this.indicesConfigs.entrySet()) {
            if (index.getValue().getMappingContent() != null) {
                try {
                    PutMappingRequest request = new PutMappingRequest(index.getValue().getIndex());
                    request.source(index.getValue().getMappingContent(), XContentType.JSON);

                    client.indices().putMapping(request, RequestOptions.DEFAULT);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
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
}
