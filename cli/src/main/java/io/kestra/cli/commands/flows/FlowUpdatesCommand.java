package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractApiCommand;
import io.kestra.cli.AbstractValidateCommand;
import io.kestra.core.serializers.YamlParser;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.netty.DefaultHttpClient;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@CommandLine.Command(
    name = "updates",
    description = "Create or update flows from a folder, and optionally delete the ones not present",
    mixinStandardHelpOptions = true
)
@Slf4j
public class FlowUpdatesCommand extends AbstractApiCommand {

    @CommandLine.Parameters(index = "0", description = "The directory containing files")
    public Path directory;

    @CommandLine.Option(names = {"--delete"}, negatable = true, description = "Whether missing should be deleted")
    public boolean delete = false;

    @SuppressWarnings("deprecation")
    @Override
    public Integer call() throws Exception {
        super.call();

        try (var files = Files.walk(directory)) {
            List<String> flows = files
                .filter(Files::isRegularFile)
                .filter(YamlParser::isValidExtension)
                .map(path -> {
                    try {
                        return IncludeHelperExpander.expand(Files.readString(path, Charset.defaultCharset()), path.getParent());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

            String body = "";
            if (flows.isEmpty()) {
                stdOut("No flow found on '{}'", directory.toFile().getAbsolutePath());
            } else {
                body = String.join("\n---\n", flows);
            }
            try(DefaultHttpClient client = client()) {
                MutableHttpRequest<String> request = HttpRequest
                    .POST(apiUri("/flows/bulk") + "?delete=" + delete, body).contentType(MediaType.APPLICATION_YAML);

                List<UpdateResult> updated = client.toBlocking().retrieve(
                    this.requestOptions(request),
                    Argument.listOf(UpdateResult.class)
                );

                stdOut(updated.size() + " flow(s) successfully updated !");
                updated.forEach(flow -> stdOut("- " + flow.getNamespace() + "."  + flow.getId()));
            } catch (HttpClientResponseException e){
                AbstractValidateCommand.handleHttpException(e, "flow");
                return 1;
            }
        } catch (ConstraintViolationException e) {
            AbstractValidateCommand.handleException(e, "flow");

            return 1;
        }

        return 0;
    }
}
