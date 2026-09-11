package io.kestra.cli.commands.servers;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.models.ServerType;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command(
    name = "local",
    description = "Start the local development server"
)
public class LocalCommand extends StandAloneCommand {
    // @FIXME: Keep it for bug in micronaut that need to have inject on top level command to inject on abstract classes
    @Inject
    private ApplicationContext applicationContext;

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        Path data = Paths.get("").toAbsolutePath().resolve("data");

        //noinspection ResultOfMethodCallIgnored
        data.toFile().mkdirs();

        return ImmutableMap.<String, Object> builder()
            .put("kestra.server-type", ServerType.STANDALONE)
            .put("kestra.repository.type", "h2")
            .put("kestra.queue.type", "h2")
            .put("kestra.storage.type", "local")
            .put("kestra.storage.local.base-path", data.toString())
            // Plugin auto-install is deliberately NOT forced here: these properties outrank both
            // the config file and system properties, so setting it would make the feature
            // impossible to turn off on this persona. The computed default (OSS + local storage,
            // which the two entries above establish) already turns it on.
            .put("datasources.h2.url", "jdbc:h2:file:" + data.resolve("database") + ";TIME ZONE=UTC;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=30000")
            .put("datasources.h2.username", "sa")
            .put("datasources.h2.password", "")
            .put("datasources.h2.driverClassName", "org.h2.Driver")
            .put("endpoints.all.port", "${random.port}")
            .build();
    }

}
