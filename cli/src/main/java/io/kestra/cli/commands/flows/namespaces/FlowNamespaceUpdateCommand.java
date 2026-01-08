package io.kestra.cli.commands.flows.namespaces;

import io.kestra.cli.AbstractValidateCommand;
import io.kestra.cli.commands.AbstractServiceNamespaceUpdateCommand;
import io.kestra.cli.services.TenantIdSelectorService;
import io.kestra.core.serializers.YamlParser;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.netty.DefaultHttpClient;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@CommandLine.Command(
    name = "update",
    description = "Update flows in namespace",
    mixinStandardHelpOptions = true
)
@Slf4j
public class FlowNamespaceUpdateCommand extends AbstractServiceNamespaceUpdateCommand {

    @CommandLine.Option(names = {"--override-namespaces"}, negatable = true, description = "Replace namespace of all flows by the one provided")
    public boolean override = false;

    @Inject
    private TenantIdSelectorService tenantService;

    @Override
    public Integer call() throws Exception {
        super.call();

        try (Stream<Path> files = Files.walk(directory)) {
            List<Path> flows = files
                .filter(Files::isRegularFile)
                .filter(YamlParser::isValidExtension)
                .toList();

            // At least one flow file is expected for update
            if (flows.isEmpty()) {
                stdErr("No flow found in ''{0}''!", directory.toFile().getAbsolutePath());
                return 1;
            }

            // Build multipart body with all available flow files
            MultipartBody.Builder bodyBuilder = MultipartBody.builder();
            flows.forEach(flow -> bodyBuilder.addPart("flows", flow.toFile().getName(), MediaType.APPLICATION_YAML_TYPE, flow.toFile()));

            // Call update API
            try (DefaultHttpClient client = client()) {
                MutableHttpRequest<MultipartBody> request = HttpRequest.POST(
                    String.format("%s/%s?override=%s&delete=%s", apiUri("/flows", tenantService.getTenantIdAndAllowEETenants(tenantId)), namespace, override, delete),
                    bodyBuilder.build()
                ).contentType(MediaType.MULTIPART_FORM_DATA);

                List<UpdateResult> updated = client.toBlocking().retrieve(
                    this.requestOptions(request),
                    Argument.listOf(UpdateResult.class)
                );

                stdOut("{0} flow(s) for namespace ''{1}'' successfully updated!", updated.size(), namespace);
                updated.forEach(flow -> stdOut("- {0}.{1}", flow.getNamespace(), flow.getId()));
            } catch (HttpClientResponseException e) {
                AbstractValidateCommand.handleHttpException(e, "flow");
                return 1;
            }
        }

        return 0;
    }
}
