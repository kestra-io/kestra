package io.kestra.cli.commands.namespaces.kv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.cli.AbstractApiCommand;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.netty.DefaultHttpClient;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@CommandLine.Command(
    name = "update",
    description = "Update value for a KV Store key",
    mixinStandardHelpOptions = true
)
@Slf4j
public class KvUpdateCommand extends AbstractApiCommand {

    @CommandLine.Parameters(index = "0", description = "The namespace to update")
    public String namespace;

    @CommandLine.Parameters(index = "1", description = "The key to update")
    public String key;

    @CommandLine.Parameters(index = "2", description = "The value to assign to the key. If the value is an object, it must be in JSON format. If the value must be read from file, use -f parameter.")
    public String value;

    @Option(names = {"-e", "--expiration"}, description = "The duration after which the key should expire.")
    public String expiration;

    @Option(names = {"-t", "--type"}, description = "The type of the value. Optional and useful to override the deduced type (eg. numbers, booleans or JSON as full string). Valid values: ${COMPLETION-CANDIDATES}.")
    public Type type;

    @Option(names = {"-f", "--file-value"}, description = "The file from which to read the value to set. If this is provided, it will take precedence over any specified value.")
    public Path fileValue;

    @Override
    public Integer call() throws Exception {
        super.call();

        if (fileValue != null) {
            value = Files.readString(Path.of(fileValue.toString().trim()));
        }

        if (isLiteral(value) || type == Type.STRING) {
            value = wrapAsJsonLiteral(value);
        }

        Duration ttl = expiration == null ? null : Duration.parse(expiration);
        MutableHttpRequest<String> request = HttpRequest
            .PUT(apiUri("/namespaces/") + namespace + "/kv/" + key, value)
            .contentType(MediaType.APPLICATION_JSON_TYPE);

        if (ttl != null) {
            request.header("ttl", ttl.toString());
        }

        try (DefaultHttpClient client = client()) {
            client.toBlocking().exchange(this.requestOptions(request));
        }
        return 0;
    }

    private static boolean isLiteral(final String input) {
        // use ION mapper to properly handle timestamp
        ObjectMapper mapper = JacksonMapper.ofIon();
        try {
            mapper.readTree(input);
            return false;
        } catch (JsonProcessingException e) {
            return true;
        }
    }

    public static String wrapAsJsonLiteral(final String input) {
        return "\"" + input.replace("\"", "\\\"") + "\"";
    }

    enum Type {
        STRING, NUMBER, BOOLEAN, DATETIME, DATE, DURATION, JSON;
    }
}
