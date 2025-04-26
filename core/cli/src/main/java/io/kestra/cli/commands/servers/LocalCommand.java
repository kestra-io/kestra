package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.ServerType;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@CommandLine.Command(
    name = "local",
    description = "Start the local development server"
)
public class LocalCommand extends StandAloneCommand {
    // @FIXME: Keep it for bug in micronaut that need to have inject on top level command to inject on abstract classe
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
            "datasources.h2.url", "jdbc:h2:file:" + data.resolve("database") + ";TIME ZONE=UTC;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=30000",
            "datasources.h2.username", "sa",
            "datasources.h2.password", "",
            "datasources.h2.driverClassName", "org.h2.Driver",
            "endpoints.all.port", "${random.port}"
        );
    }

}
