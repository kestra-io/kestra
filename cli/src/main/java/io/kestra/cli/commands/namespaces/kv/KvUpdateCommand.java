package io.kestra.cli.commands.namespaces.kv;

import io.kestra.cli.AbstractApiCommand;
import io.kestra.cli.AbstractValidateCommand;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStoreValueWrapper;
import io.kestra.core.utils.KestraIgnore;
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
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

@CommandLine.Command(
    name = "update",
    description = "update value for a KV Store key",
    mixinStandardHelpOptions = true
)
@Slf4j
public class KvUpdateCommand extends AbstractApiCommand {
    private static final Pattern STRING_PATTERN = Pattern.compile("^(?![\\d{\\[\"]+)(?!false)(?!true)(?!P(?=[^T]|T.)(?:\\d*D)?(?:T(?=.)(?:\\d*H)?(?:\\d*M)?(?:\\d*S)?)?).*$");

    @CommandLine.Parameters(index = "0", description = "the namespace to update")
    public String namespace;

    @CommandLine.Parameters(index = "1", description = "the key to update")
    public String key;

    @CommandLine.Parameters(index = "2", description = "the value to assign to the key. If the value is an object, it must be in JSON format. If the value must be read from file, use -f parameter.")
    public String value;

    @CommandLine.Option(names = {"-e", "--expiration"}, description = "the duration after which the key should expire.")
    public String expiration;

    @CommandLine.Option(names = {"-t", "--type"}, description = "the type of the value. Optional and useful to override the deduced type (eg. numbers, booleans or JSON as full string). Must be one of STRING, NUMBER, BOOLEAN, DATETIME, DATE, DURATION, JSON.")
    public String type;

    @CommandLine.Option(names = {"-f", "--file-value"}, description = "the file from which to read the value to set. If this is provided, it will take precedence over any specified value.")
    public Path fileValue;

    @Override
    public Integer call() throws Exception {
        super.call();

        if (fileValue != null) {
            value = Files.readString(Path.of(fileValue.toString().trim()));
        }

        String formattedValue = value;
        if (type != null) {
            if (type.trim().equals("STRING") && !value.startsWith("\"")) {
                formattedValue = "\"" + value.replace("\"", "\\\"") + "\"";
            }
        } else if (STRING_PATTERN.matcher(value).matches()) {
            formattedValue = "\"" + value + "\"";
        }

        Duration ttl = expiration == null ? null : Duration.parse(expiration);
        String trimmedValue = formattedValue.trim();
        boolean isJson = trimmedValue.startsWith("{") && trimmedValue.endsWith("}") || trimmedValue.startsWith("[") && trimmedValue.endsWith("]");
        MutableHttpRequest<String> request = HttpRequest.PUT(apiUri("/namespaces/") + namespace + "/kv/" + key, formattedValue)
            .contentType(isJson ? MediaType.APPLICATION_JSON_TYPE : MediaType.TEXT_PLAIN_TYPE);

        if (ttl != null) {
            request.header("ttl", ttl.toString());
        }

        try (DefaultHttpClient client = client()) {
            client.toBlocking().exchange(this.requestOptions(request));
        }

        return 0;
    }
}
