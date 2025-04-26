package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractApiCommand;
import io.kestra.cli.AbstractValidateCommand;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.netty.DefaultHttpClient;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

@CommandLine.Command(
    name = "export",
    description = "Export flows to a ZIP file",
    mixinStandardHelpOptions = true
)
@Slf4j
public class FlowExportCommand extends AbstractApiCommand {
    private static final String DEFAULT_FILE_NAME = "flows.zip";

    // @FIXME: Keep it for bug in micronaut that need to have inject on top level command to inject on abstract classe
    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Option(names = {"--namespace"}, description = "The namespace of flows to export")
    public String namespace;

    @CommandLine.Parameters(index = "0", description = "The directory to export the ZIP file to")
    public Path directory;

    @Override
    public Integer call() throws Exception {
        super.call();

        try(DefaultHttpClient client = client()) {
            MutableHttpRequest<Object> request = HttpRequest
                .GET(apiUri("/flows/export/by-query") + (namespace != null ? "?namespace=" + namespace : ""))
                .accept(MediaType.APPLICATION_OCTET_STREAM);

            HttpResponse<byte[]> response = client.toBlocking().exchange(this.requestOptions(request), byte[].class);
            Path zipFile = Path.of(directory.toString(), DEFAULT_FILE_NAME);
            zipFile.toFile().createNewFile();
            Files.write(zipFile, response.body());

            stdOut("Exporting flow(s) for namespace '" + namespace + "' successfully done !");
        } catch (HttpClientResponseException e) {
            AbstractValidateCommand.handleHttpException(e, "flow");
            return 1;
        }

        return 0;
    }

}
