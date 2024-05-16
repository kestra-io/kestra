package io.kestra.plugin.core.http;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.http.*;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.ssl.ClientSslConfiguration;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.reactor.http.client.ReactorStreamingHttpClient;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
abstract public class AbstractHttp extends Task implements HttpInterface {
    @NotNull
    protected String uri;

    @Builder.Default
    protected HttpMethod method = HttpMethod.GET;

    protected String body;

    protected Map<String, Object> formData;

    protected String contentType;

    protected Map<CharSequence, CharSequence> headers;

    protected RequestOptions options;

    protected SslOptions sslOptions;


    protected DefaultHttpClientConfiguration configuration(RunContext runContext, HttpMethod httpMethod) throws IllegalVariableEvaluationException {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();

        if (this.options != null) {
            if (this.options.getConnectTimeout() != null) {
                configuration.setConnectTimeout(this.options.getConnectTimeout());
            }

            if (this.options.getReadTimeout() != null) {
                configuration.setReadTimeout(this.options.getReadTimeout());
            }

            if (this.options.getReadIdleTimeout() != null) {
                configuration.setReadIdleTimeout(this.options.getReadIdleTimeout());
            }

            if (this.options.getConnectionPoolIdleTimeout() != null) {
                configuration.setConnectionPoolIdleTimeout(this.options.getConnectionPoolIdleTimeout());
            }

            if (this.options.getMaxContentLength() != null) {
                configuration.setMaxContentLength(this.options.getMaxContentLength());
            }

            if (this.options.getProxyType() != null) {
                configuration.setProxyType(this.options.getProxyType());
            }

            if (this.options.getProxyAddress() != null && this.options.getProxyPort() != null) {
                configuration.setProxyAddress(new InetSocketAddress(
                    runContext.render(this.options.getProxyAddress()),
                    this.options.getProxyPort()
                ));
            }

            if (this.options.getProxyUsername() != null) {
                configuration.setProxyUsername(runContext.render(this.options.getProxyUsername()));
            }

            if (this.options.getProxyPassword() != null) {
                configuration.setProxyPassword(runContext.render(this.options.getProxyPassword()));
            }

            if (this.options.getDefaultCharset() != null) {
                configuration.setDefaultCharset(this.options.getDefaultCharset());
            }

            if (this.options.getFollowRedirects() != null) {
                configuration.setFollowRedirects(this.options.getFollowRedirects());
            }

            if (this.options.getLogLevel() != null) {
                configuration.setLogLevel(this.options.getLogLevel());
            }
        }

        if (httpMethod == HttpMethod.HEAD) {
            configuration.setMaxContentLength(Integer.MAX_VALUE);
        }

        ClientSslConfiguration clientSslConfiguration = new ClientSslConfiguration();

        if (this.sslOptions != null) {
            if (this.sslOptions.getInsecureTrustAllCertificates() != null) {
                clientSslConfiguration.setInsecureTrustAllCertificates(this.sslOptions.getInsecureTrustAllCertificates());
            }
        }

        configuration.setSslConfiguration(clientSslConfiguration);

        return configuration;
    }

    protected HttpClient client(RunContext runContext, HttpMethod httpMethod) throws IllegalVariableEvaluationException, MalformedURLException, URISyntaxException {
        URI from = new URI(runContext.render(this.uri));

        return ReactorHttpClient.create(from.toURL(), this.configuration(runContext, httpMethod));
    }

    protected ReactorStreamingHttpClient streamingClient(RunContext runContext, HttpMethod httpMethod) throws IllegalVariableEvaluationException, MalformedURLException, URISyntaxException {
        URI from = new URI(runContext.render(this.uri));

        return ReactorStreamingHttpClient.create(from.toURL(), this.configuration(runContext, httpMethod));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected HttpRequest request(RunContext runContext) throws IllegalVariableEvaluationException, URISyntaxException, IOException {
        URI from = new URI(runContext.render(this.uri));

        MutableHttpRequest request = HttpRequest
            .create(method, from.toString());


        if (this.options != null && this.options.getBasicAuthUser() != null && this.options.getBasicAuthPassword() != null) {
            request.basicAuth(
                runContext.render(this.options.getBasicAuthUser()),
                runContext.render(this.options.getBasicAuthPassword())
            );
        }

        if (this.formData != null) {
            if (MediaType.MULTIPART_FORM_DATA.equals(this.contentType)) {
                request.contentType(MediaType.MULTIPART_FORM_DATA);

                MultipartBody.Builder builder = MultipartBody.builder();
                for (Map.Entry<String, Object> e : this.formData.entrySet()) {
                    String key = runContext.render(e.getKey());

                    if (e.getValue() instanceof String) {
                        String render = runContext.render((String) e.getValue());

                        if (render.startsWith("kestra://")) {
                            File tempFile = runContext.tempFile().toFile();

                            try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                                IOUtils.copyLarge(runContext.storage().getFile(new URI(render)), outputStream);
                            }

                            builder.addPart(key, tempFile);
                        } else {
                            builder.addPart(key, render);
                        }
                    } else if (e.getValue() instanceof Map && ((Map<String, String>) e.getValue()).containsKey("name") && ((Map<String, String>) e.getValue()).containsKey("content")) {
                        String name = runContext.render(((Map<String, String>) e.getValue()).get("name"));
                        String content = runContext.render(((Map<String, String>) e.getValue()).get("content"));

                        File tempFile = runContext.tempFile().toFile();
                        File renamedFile = new File(Files.move(tempFile.toPath(), tempFile.toPath().resolveSibling(name)).toUri());

                        try (OutputStream outputStream = new FileOutputStream(renamedFile)) {
                            IOUtils.copyLarge(runContext.storage().getFile(new URI(content)), outputStream);
                        }

                        builder.addPart(key, renamedFile);
                    } else {
                        builder.addPart(key, JacksonMapper.ofJson().writeValueAsString(e.getValue()));
                    }
                }

                request.body(builder.build());
            } else {
                request.contentType(MediaType.APPLICATION_FORM_URLENCODED);
                request.body(runContext.render(this.formData));
            }
        } else if (this.body != null) {
            request.body(runContext.render(body));
        }

        if (this.contentType != null) {
            request.contentType(runContext.render(this.contentType));
        }

        if (this.headers != null) {
            request.headers(this.headers
                .entrySet()
                .stream()
                .map(throwFunction(e -> new AbstractMap.SimpleEntry<>(
                    e.getKey(),
                    runContext.render(e.getValue().toString())
                )))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            );
        }

        return request;
    }

    protected String[] tags(HttpRequest<String> request, HttpResponse<String> response) {
        ArrayList<String> tags = new ArrayList<>(
            Arrays.asList("request.method", request.getMethodName())
        );

        if (response != null) {
            tags.addAll(
                Arrays.asList("response.code", String.valueOf(response.getStatus().getCode()))
            );
        }

        return tags.toArray(String[]::new);
    }
}
