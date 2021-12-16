package io.kestra.repository.elasticsearch.services;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestHighLevelClient;
import io.kestra.repository.elasticsearch.configs.ElasticsearchConfig;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;


@Requires(beans = ElasticsearchConfig.class)
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
