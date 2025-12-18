package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractApiCommand;
import io.kestra.cli.AbstractValidateCommand;
import io.kestra.cli.services.TenantIdSelectorService;
import io.kestra.core.utils.ListUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.netty.DefaultHttpClient;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "updateFromSource",
    description = "Update a single flow",
    mixinStandardHelpOptions = true
)
@Slf4j
public class FlowsUpdateFromSourceCommand extends AbstractApiCommand {

    @Inject
    private TenantIdSelectorService tenantService;

    @SuppressWarnings("deprecation")
    @Override
    public Integer call() throws Exception {
        super.call();

        try(DefaultHttpClient client = client()) {
            stdOut("Exporting all sources...");
            MutableHttpRequest<Object> exportRequest = HttpRequest
                .GET(apiUri("/flows/export/by-query", tenantService.getTenantId(tenantId)))
                .accept(MediaType.APPLICATION_OCTET_STREAM);

            HttpResponse<byte[]> response = client.toBlocking().exchange(
                this.requestOptions(exportRequest),
                byte[].class
            );

            byte[] zipBytes = response.body();
            if (zipBytes == null || zipBytes.length == 0) {
                return 0;
            }

            List<String> flowSources = new ArrayList<>();
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        zis.closeEntry();
                        continue;
                    }

                    String name = entry.getName();
                    if (!name.endsWith(".yml") && !name.endsWith(".yaml")) {
                        zis.closeEntry();
                        continue;
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    zis.transferTo(baos);

                    String content = baos.toString(StandardCharsets.UTF_8);
                    // Ensure exactly one trailing newline. We need this new line
                    // because when we update a flow from its source,
                    // we don't update it if no change is detected.
                    // The goal here is to force an update from the source for every flows
                    content = content + System.lineSeparator();

                    flowSources.add(content);
                    zis.closeEntry();
                }
            }
            stdOut("Sources have been successfully exported.");
            stdOut("%s flows have been exported and will be updated".formatted(flowSources.size()));

            int batchSize = 500;
            List<List<String>> batches = ListUtils.partition(flowSources, batchSize);
            for (List<String> batch : batches) {
                String body = String.join("---", batch);

                MutableHttpRequest<String> bulkUpdateRequest = HttpRequest
                    .POST(apiUri("/flows/bulk?delete=false", tenantService.getTenantId(tenantId)), body).contentType(MediaType.APPLICATION_YAML);

                List<UpdateResult> updated = client.toBlocking().retrieve(
                    this.requestOptions(bulkUpdateRequest),
                    Argument.listOf(UpdateResult.class)
                );

                stdOut("%s flow(s) successfully updated!".formatted(updated.size()));
                updated.forEach(flow -> stdOut("- %s.%s".formatted(flow.getNamespace(), flow.getId())));
            }
            stdOut("All %s flows have been successfully updated!".formatted(flowSources.size()));
        } catch (HttpClientResponseException e){
            AbstractValidateCommand.handleHttpException(e, "flow");
            return 1;
        }

        return 0;
    }

}
