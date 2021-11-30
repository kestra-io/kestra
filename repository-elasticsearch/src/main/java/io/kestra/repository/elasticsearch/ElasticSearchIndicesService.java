package io.kestra.repository.elasticsearch;

import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.client.indices.PutMappingRequest;
import org.opensearch.common.xcontent.XContentType;
import io.kestra.repository.elasticsearch.configs.IndicesConfig;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.micronaut.core.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticSearchIndicesService {
    private final Map<String, IndicesConfig> indicesConfigs;

    private final RestHighLevelClient client;

    @Inject
    public ElasticSearchIndicesService(
        RestHighLevelClient client,
        List<IndicesConfig> indicesConfigs
    ) {
        this.client = client;

        this.indicesConfigs = indicesConfigs
            .stream()
            .map(r -> new AbstractMap.SimpleEntry<>(r.getName(), r))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public <T> Map<String, IndicesConfig> findConfig(Class<T> cls) {
        return indicesConfigs
            .entrySet()
            .stream()
            .filter(r -> r.getValue().getCls() == cls)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void createIndice(@Nullable Map<String, IndicesConfig> indicesConfigs) {
        try {
            for (Map.Entry<String, IndicesConfig> index : (indicesConfigs == null ? this.indicesConfigs : indicesConfigs).entrySet()) {
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

    public void updateMapping(@Nullable Map<String, IndicesConfig> indicesConfigs) {
        for (Map.Entry<String, IndicesConfig> index : (indicesConfigs == null ? this.indicesConfigs : indicesConfigs).entrySet()) {
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
