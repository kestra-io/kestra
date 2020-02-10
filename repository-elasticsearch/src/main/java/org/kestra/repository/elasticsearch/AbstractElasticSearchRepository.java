package org.kestra.repository.elasticsearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.serializers.JacksonMapper;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

abstract public class AbstractElasticSearchRepository <T> {
    protected static final ObjectMapper mapper = JacksonMapper.ofJson();
    protected Class<T> cls;

    protected RestHighLevelClient client;

    protected Map<String, IndicesConfig> indicesConfigs;

    @Inject
    public AbstractElasticSearchRepository(
        RestHighLevelClient client,
        List<IndicesConfig> indicesConfigs,
        Class<T> cls
    ) {
        this.client = client;
        this.cls = cls;

        this.indicesConfigs = indicesConfigs
            .stream()
            .filter(r -> r.getCls() == this.cls)
            .map(r -> new AbstractMap.SimpleEntry<>(r.getName(), r))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    protected Optional<T> getRequest(String index, String id) {
        try {
            GetResponse response = client.get(new GetRequest(this.indicesConfigs.get(index).getIndex(), id), RequestOptions.DEFAULT);

            if (!response.isExists()) {
                return Optional.empty();
            }

            return Optional.of(mapper.readValue(response.getSourceAsString(), this.cls));
        } catch (IOException e) {
            throw new RuntimeException(e);
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
            return client.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected DeleteResponse deleteRequest(String index, String id) {
        DeleteRequest request = new DeleteRequest(this.indicesConfigs.get(index).getIndex(), id);
        request.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        try {
            return client.delete(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

        SearchRequest searchRequest = searchRequest(index, sourceBuilder, true);
        try {
            SearchResponse searchResponse;
            searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            do {
                result.addAll(this.map(searchResponse.getHits().getHits()));

                SearchScrollRequest searchScrollRequest = new SearchScrollRequest()
                    .scrollId(searchResponse.getScrollId())
                    .scroll(new TimeValue(60000));

                searchResponse = client.scroll(searchScrollRequest, RequestOptions.DEFAULT);
            } while (searchResponse.getHits().getHits().length != 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return result;
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
                    if (index.getValue().getSettings() != null) {
                        request.settings(index.getValue().getSettings(), XContentType.JSON);
                    }

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
}
