package io.kestra.cli.commands.namespaces.files;

import io.kestra.cli.AbstractApiCommand;
import io.kestra.cli.AbstractValidateCommand;
import io.kestra.core.utils.KestraIgnore;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.netty.DefaultHttpClient;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@CommandLine.Command(
    name = "update",
    description = "Update namespace files",
    mixinStandardHelpOptions = true
)
@Slf4j
public class NamespaceFilesUpdateCommand extends AbstractApiCommand {
    @CommandLine.Parameters(index = "0", description = "The namespace to update")
    public String namespace;

    @CommandLine.Parameters(index = "1", description = "The local directory containing files for current namespace")
    public Path from;

    @CommandLine.Parameters(index = "2", description = "The remote namespace path to upload files to", defaultValue = "/")
    public String to;

    @CommandLine.Option(names = {"--delete"}, negatable = true, description = "Whether missing should be deleted")
    public boolean delete = false;

    private static final String KESTRA_IGNORE_FILE = ".kestraignore";

    @Override
    public Integer call() throws Exception {
        super.call();
        to = to.startsWith("/") ? to : "/" + to;
        to = to.endsWith("/") ? to : to + "/";

        try (var files = Files.walk(from); DefaultHttpClient client = client()) {
            if (delete) {
                client.toBlocking().exchange(this.requestOptions(HttpRequest.DELETE(apiUri("/namespaces/") + namespace + "/files?path=" + to, null)));
            }

            KestraIgnore kestraIgnore = new KestraIgnore(from);

            List<Path> paths = files
                .filter(Files::isRegularFile)
                .filter(path -> !kestraIgnore.isIgnoredFile(path.toString(), true))
                .toList();
            paths.forEach(path -> {
                MultipartBody body = MultipartBody.builder()
                    .addPart("fileContent", path.toFile())
                    .build();
                String relativizedPath = from.relativize(path).toString();
                String destination = to + relativizedPath;
                client.toBlocking().exchange(
                    this.requestOptions(
                        HttpRequest.POST(
                            apiUri("/namespaces/") + namespace + "/files?path=" + destination,
                            body
                        ).contentType(MediaType.MULTIPART_FORM_DATA)
                    )
                );
                stdOut("Successfully uploaded {0} to {1}", path.toString(), destination);
            });
        } catch (HttpClientResponseException e) {
            AbstractValidateCommand.handleHttpException(e, "namespace");
            return 1;
        }

        return 0;
    }
}
