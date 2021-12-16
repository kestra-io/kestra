package io.kestra.cli.listeners;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.event.annotation.EventListener;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@Singleton
@Slf4j
@Requires(property = "kestra.configurations.delete-files-on-start", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
public class DeleteConfigurationApplicationListeners {
    @Inject
    Environment environment;

    @EventListener
    public void onStartupEvent(StartupEvent event) throws IOException {
        environment.getPropertySources()
            .forEach(throwConsumer(source -> {
                Path path = Path.of(source.getName());

                boolean exists = Files.exists(path);

                if (exists) {
                    Files.delete(path);
                }
            }));
    }
}

