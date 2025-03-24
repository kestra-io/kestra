package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractApiCommand;
import io.kestra.cli.AbstractValidateCommand;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.netty.DefaultHttpClient;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

@CommandLine.Command(
    name = "update",
    description = "Update a single flow",
    mixinStandardHelpOptions = true
)
@Slf4j
public class FlowUpdateCommand extends AbstractApiCommand {
    @CommandLine.Parameters(index = "0", description = "The file containing the flow")
    public Path flowFile;

    @CommandLine.Parameters(index = "1", description = "The namespace of the flow")
    public String namespace;

    @CommandLine.Parameters(index = "2", description = "The ID of the flow")
    public String id;

    @SuppressWarnings("deprecation")
    @Override
    public Integer call() throws Exception {
        super.call();

        checkFile();

        String body = Files.readString(flowFile);

        try(DefaultHttpClient client = client()) {
            MutableHttpRequest<String> request = HttpRequest
                .PUT(apiUri("/flows/" + namespace + "/" + id ), body).contentType(MediaType.APPLICATION_YAML);

            client.toBlocking().retrieve(
                this.requestOptions(request),
                String.class
            );

            stdOut("Flow successfully updated !");
        } catch (HttpClientResponseException e){
            AbstractValidateCommand.handleHttpException(e, "flow");
            return 1;
        }

        return 0;
    }

    protected void checkFile() {
        if (!Files.isRegularFile(flowFile)) {
            throw new IllegalArgumentException("The file '" + flowFile.toFile().getAbsolutePath() + "' is not a file");
        }
    }
}
