package io.kestra.cli.commands.flows.namespaces;

import io.kestra.cli.AbstractApiCommand;
import io.kestra.cli.commands.flows.FlowValidateCommand;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.netty.DefaultHttpClient;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

@CommandLine.Command(
    name = "extract",
    description = "extract namespace flows",
    mixinStandardHelpOptions = true
)
@Slf4j
public class FlowNamespaceExtractCommand extends AbstractApiCommand {
    private static final String DEFAULT_FILE_NAME = "flows.zip";

    @CommandLine.Parameters(index = "0", description = "the namespace of templates to extract")
    public String namespace;

    @CommandLine.Parameters(index = "1", description = "the directory to extract the file to")
    public Path directory;

    @Override
    public Integer call() throws Exception {
        super.call();

        try(DefaultHttpClient client = client()) {
            MutableHttpRequest<Object> request = HttpRequest
                .GET("/api/v1/flows/extract/by-query?namespace=" + namespace).accept(MediaType.APPLICATION_OCTET_STREAM);

            HttpResponse<byte[]> response = client.toBlocking().exchange(this.requestOptions(request), byte[].class);
            Path zipFile = Path.of(directory.toString(), DEFAULT_FILE_NAME);
            zipFile.toFile().createNewFile();
            Files.write(zipFile, response.body());

            stdOut("Extracted flow(s) for namespace '" + namespace + "' successfully done !");
        } catch (HttpClientResponseException e) {
            FlowValidateCommand.handleHttpException(e, "flow");
            return 1;
        }

        return 0;
    }

}
