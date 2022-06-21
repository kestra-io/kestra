package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.ServerType;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.StandAloneRunner;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@CommandLine.Command(
    name = "local",
    description = "start a local server"
)
public class LocalCommand extends StandAloneCommand {
    // Keep it for bug in micronaut
    @Inject
    private ApplicationContext applicationContext;

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        Path data = Paths.get("").toAbsolutePath().resolve("data");

        //noinspection ResultOfMethodCallIgnored
        data.toFile().mkdirs();

        return ImmutableMap.of(
            "kestra.server-type", ServerType.STANDALONE,
            "kestra.repository.type", "h2",
            "kestra.queue.type", "h2",
            "kestra.storage.type", "local",
            "kestra.storage.local.base-path", data.toString(),
            "datasources.h2.url", "jdbc:h2:file:" + data.resolve("database") + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "datasources.h2.username", "sa",
            "datasources.h2.password", "",
            "datasources.h2.driverClassName", "org.h2.Driver",
            "endpoints.all.port", "${random.port}"
        );
    }

}
