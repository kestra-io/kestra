package io.kestra.repository.elasticsearch.services;

import io.micronaut.elasticsearch.DefaultElasticsearchClientFactory;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import io.kestra.repository.elasticsearch.configs.ElasticsearchConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A replacement for {@link DefaultElasticsearchClientFactory} for creating Elasticsearch client.
 * The original is incomplete and don't allow basic auth.
 */
@Requires(beans = ElasticsearchConfig.class)
@Replaces(DefaultElasticsearchClientFactory.class)
@Factory
public class ElasticsearchClientFactory {
    /**
     * Create the {@link RestHighLevelClient} bean for the given configuration.
     *
     * @param config The {@link ElasticsearchConfig} object
     * @return A {@link RestHighLevelClient} bean
     */
    @Bean(preDestroy = "close")
    @Inject
    @Singleton
    RestHighLevelClient restHighLevelClient(ElasticsearchConfig config) {
        return new RestHighLevelClient(restClientBuilder(config));
    }

    /**
     * @param config The {@link ElasticsearchConfig} object
     * @return The {@link RestClient} bean
     */
    @Bean(preDestroy = "close")
    @Inject
    @Singleton
    RestClient restClient(ElasticsearchConfig config) {
        return restClientBuilder(config).build();
    }

    /**
     * @param config The {@link ElasticsearchConfig} object
     * @return The {@link RestClientBuilder}
     */
    protected RestClientBuilder restClientBuilder(ElasticsearchConfig config) {
        RestClientBuilder builder = RestClient
            .builder(config.httpHosts())
            .setRequestConfigCallback(requestConfigBuilder -> {
                requestConfigBuilder = config.getRequestConfigBuilder();
                return requestConfigBuilder;
            })
            .setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder = config.httpAsyncClientBuilder();
                return httpClientBuilder;
            });

        if (config.getDefaultHeaders() != null) {
            builder.setDefaultHeaders(config.defaultHeaders());
        }

        if (config.getPathPrefix() != null) {
            builder.setPathPrefix(config.getPathPrefix());
        }

        if (config.getPathPrefix() != null) {
            builder.setPathPrefix(config.getPathPrefix());
        }

        if (config.getStrictDeprecationMode() != null) {
            builder.setStrictDeprecationMode(config.getStrictDeprecationMode());
        }

        return builder;
    }

}
