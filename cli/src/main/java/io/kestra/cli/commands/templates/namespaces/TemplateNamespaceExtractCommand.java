package io.kestra.cli.commands.templates.namespaces;

import io.kestra.cli.AbstractApiCommand;
import io.kestra.cli.commands.AbstractServiceNamespaceUpdateCommand;
import io.kestra.cli.commands.flows.FlowValidateCommand;
import io.kestra.cli.commands.templates.TemplateValidateCommand;
import io.kestra.core.models.templates.Template;
import io.kestra.core.serializers.YamlFlowParser;
import io.micronaut.core.type.Argument;
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
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolationException;

@CommandLine.Command(
    name = "extract",
    description = "extract namespace templates",
    mixinStandardHelpOptions = true
)
@Slf4j
public class TemplateNamespaceExtractCommand extends AbstractApiCommand {
    private static final String DEFAULT_FILE_NAME = "templates.zip";

    @CommandLine.Parameters(index = "0", description = "the namespace of templates to extract")
    public String namespace;

    @CommandLine.Parameters(index = "1", description = "the directory to extract the file to")
    public Path directory;

    @Override
    public Integer call() throws Exception {
        super.call();

        try(DefaultHttpClient client = client()) {
            MutableHttpRequest<Object> request = HttpRequest
                .GET("/api/v1/templates/extract/by_query?namespace=" + namespace).accept(MediaType.APPLICATION_OCTET_STREAM);

            HttpResponse<byte[]> response = client.toBlocking().exchange(this.requestOptions(request), byte[].class);
            Path zipFile = Path.of(directory.toString(), DEFAULT_FILE_NAME);
            zipFile.toFile().createNewFile();
            Files.write(zipFile, response.body());

            stdOut("Extracted template(s) for namespace '" + namespace + "' successfully done !");
        } catch (HttpClientResponseException e) {
            TemplateValidateCommand.handleHttpException(e, "template");
            return 1;
        }

        return 0;
    }

}
