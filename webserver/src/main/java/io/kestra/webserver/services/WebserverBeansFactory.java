package io.kestra.webserver.services;

import io.kestra.core.plugins.PluginCatalogService;
import io.kestra.core.utils.ExecutorsUtils;

import io.micronaut.context.annotation.Factory;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
public class WebserverBeansFactory {

    @Singleton
    @Named("withIcons")
    public PluginCatalogService pluginCatalogServiceWithIcons(@Client("api") HttpClient httpClient, ExecutorsUtils executorsUtils) {
        return new PluginCatalogService(httpClient, true, true, executorsUtils);
    }
}
