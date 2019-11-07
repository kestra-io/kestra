package org.floworc.repository.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.serializers.JacksonMapper;
import org.floworc.repository.elasticsearch.configs.IndicesConfig;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

abstract public class AbstractElasticSearchRepository <T> implements FlowRepositoryInterface {
    protected static final ObjectMapper mapper = JacksonMapper.ofJson();
    protected Class<T> cls;

    protected RestHighLevelClient client;

    protected IndicesConfig indicesConfig;

    @Inject
    public AbstractElasticSearchRepository(
        RestHighLevelClient client,
        List<IndicesConfig> indicesConfigs
    ) {
        this.client = client;

        this.indicesConfig = indicesConfigs
            .stream()
            .filter(r -> r.getCls().equals(this.getClass().getName().toLowerCase().replace(".", "-")))
            .findFirst()
            .orElseThrow();
    }

    private SearchRequest searchRequest(SearchSourceBuilder sourceBuilder, boolean scroll) {
        SearchRequest searchRequest = new SearchRequest()
            .indices(this.indicesConfig.getName())
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

    protected List<T> query(SearchSourceBuilder sourceBuilder) {
        SearchRequest searchRequest = searchRequest(sourceBuilder, false);

        try {
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            return this.map(searchResponse.getHits().getHits());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected List<T> scroll(SearchSourceBuilder sourceBuilder) {
        List<T> result = new ArrayList<>();

        SearchRequest searchRequest = searchRequest(sourceBuilder, true);
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
            GetIndexRequest exists = new GetIndexRequest(this.indicesConfig.getName());
            if (!client.indices().exists(exists, RequestOptions.DEFAULT)) {
                CreateIndexRequest request = new CreateIndexRequest(this.indicesConfig.getName());
                if (this.indicesConfig.getSettings() != null) {
                    request.settings(this.indicesConfig.getSettings(), XContentType.JSON);
                }

                client.indices().create(request, RequestOptions.DEFAULT);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateMapping() {
        if (this.indicesConfig.getMapping() != null) {
            try {
                PutMappingRequest request = new PutMappingRequest(this.indicesConfig.getName());
                request.source(this.indicesConfig.getMapping(), XContentType.JSON);

                client.indices().putMapping(request, RequestOptions.DEFAULT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
