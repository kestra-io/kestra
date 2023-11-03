package io.kestra.cli.commands.namespaces.files;

import io.kestra.cli.AbstractValidateCommand;
import io.kestra.cli.commands.AbstractServiceNamespaceUpdateCommand;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.netty.DefaultHttpClient;
import lombok.extern.slf4j.Slf4j;
import nl.basjes.gitignore.GitIgnore;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@CommandLine.Command(
    name = "update",
    description = "update namespace files",
    mixinStandardHelpOptions = true
)
@Slf4j
public class NamespaceFilesUpdateCommand extends AbstractServiceNamespaceUpdateCommand {

    private static final String KESTRA_IGNORE_FILE = ".kestraignore";

    @Override
    public Integer call() throws Exception {
        super.call();

        try (var files = Files.walk(directory); DefaultHttpClient client = client()) {
            if (delete) {
                client.toBlocking().exchange(this.requestOptions(HttpRequest.DELETE(apiUri("/namespaces/") + namespace + "/files?path=/", null)));
            }

            GitIgnore gitIgnore = parseKestraIgnore(directory);

            List<Path> paths = files
                .filter(Files::isRegularFile)
                .filter(path -> !path.endsWith(KESTRA_IGNORE_FILE))
                .filter(path -> !Boolean.TRUE.equals(gitIgnore.isIgnoredFile(path.toString())))
                .toList();
            paths.forEach(path -> {
                MultipartBody body = MultipartBody.builder()
                    .addPart("fileContent", path.toFile())
                    .build();
                Path dest = directory.relativize(path);
                client.toBlocking().exchange(
                    this.requestOptions(
                        HttpRequest.POST(
                            "/api/v1/namespaces/" + namespace + "/files?path=/" + dest,
                            body
                        ).contentType(MediaType.MULTIPART_FORM_DATA)
                    )
                );
                stdOut("Successfully uploaded {0} to /{1}", path, dest);
            });
        } catch (HttpClientResponseException e) {
            AbstractValidateCommand.handleHttpException(e, "namespace");
            return 1;
        }

        return 0;
    }

    private GitIgnore parseKestraIgnore(Path directory) throws IOException {
        File kestraIgnoreFile = Path.of(directory.toString(), KESTRA_IGNORE_FILE).toFile();
        if (!kestraIgnoreFile.exists()) {
            return new GitIgnore("");
        }
        return new GitIgnore(kestraIgnoreFile);
    }
}
