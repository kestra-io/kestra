package io.kestra.cli;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.netty.DefaultHttpClient;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import picocli.CommandLine;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class AbstractApiCommand extends AbstractCommand {
    @CommandLine.Option(names = {"--server"}, description = "Kestra server url", defaultValue = "http://localhost:8080")
    protected URL server;

    @CommandLine.Option(names = {"--headers"}, paramLabel = "<name=value>", description = "Headers to add to the request")
    protected Map<CharSequence, CharSequence> headers;

    @CommandLine.Option(names = {"--user"}, paramLabel = "<user:password>", description = "Server user and password")
    protected String user;

    @CommandLine.Option(names = {"--tenant"}, description = "Tenant identifier (EE only, when multi-tenancy is enabled)")
    protected String tenantId;

    @Inject
    @Named("remote-api")
    @Nullable
    private HttpClientConfiguration httpClientConfiguration;

    protected DefaultHttpClient client() throws URISyntaxException {
        return new DefaultHttpClient(server.toURI(), httpClientConfiguration != null ? httpClientConfiguration : new DefaultHttpClientConfiguration());
    }

    protected <T> HttpRequest<T> requestOptions(MutableHttpRequest<T> request) {
        if (this.headers != null) {
            request.headers(this.headers);
        }

        if (this.user != null) {
            List<String> split = Arrays.asList(this.user.split(":"));
            String user = split.get(0);
            String password = String.join(":", split.subList(1, split.size()));

            request.basicAuth(user, password);
        }

        return request;
    }

    protected String apiUri(String path) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("'path' must be non-null and start with '/'");
        }

        return tenantId == null ? "/api/v1" + path : "/api/v1/" + tenantId + path;
    }
}
