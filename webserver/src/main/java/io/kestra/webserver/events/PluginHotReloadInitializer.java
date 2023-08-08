package io.kestra.webserver.events;

import io.kestra.core.contexts.KestraApplicationContext;
import io.kestra.webserver.controllers.PluginController;
import io.micronaut.cache.CacheManager;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.discovery.event.ServiceReadyEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class PluginHotReloadInitializer implements ApplicationEventListener<ServiceReadyEvent> {
    @Inject
    private ApplicationContext applicationContext;
    @Inject
    private Optional<CacheManager<?>> cacheManager;

    @Override
    public void onApplicationEvent(ServiceReadyEvent event) {
        if (applicationContext instanceof KestraApplicationContext kestraApplicationContext &&
                kestraApplicationContext.getPluginRegistry() != null &&
                cacheManager.isPresent()
        ) {
            kestraApplicationContext
                .getPluginRegistry()
                .setCacheCleaner(cacheManager.get().getCache(PluginController.PLUGINS_CACHE_KEY)::invalidateAll);
        }
    }
}