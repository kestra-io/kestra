package org.kestra.repository.elasticsearch.configs;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.message.BasicHeader;

import java.net.URI;
import java.util.Arrays;

@ConfigurationProperties("kestra.elasticsearch.client")
@Getter
public class ElasticsearchConfig {
    String[] httpHosts;
    String[] defaultHeaders;
    String pathPrefix;
    Boolean strictDeprecationMode;

    BasicAuth basicAuth;

    @Getter
    @ConfigurationProperties("basic-auth")
    public static class BasicAuth {
        String username;
        String password;
    }

    @ConfigurationBuilder(configurationPrefix = "request.default")
    @SuppressWarnings("WeakerAccess")
    protected RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();

    public HttpAsyncClientBuilder httpAsyncClientBuilder() {
        HttpAsyncClientBuilder builder = HttpAsyncClientBuilder.create();

        if (basicAuth != null) {
            final CredentialsProvider basicCredential = new BasicCredentialsProvider();
            basicCredential.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(this.basicAuth.username, this.basicAuth.password)
            );

            builder.setDefaultCredentialsProvider(basicCredential);
        }

        return builder;
    }

    public HttpHost[] httpHosts() {
        return Arrays.stream(this.httpHosts)
            .map(s -> {
                URI uri = URI.create(s);
                return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
            })
            .toArray(HttpHost[]::new);
    }

    public Header[] defaultHeaders() {
        return Arrays.stream(this.httpHosts)
            .map(header -> {
                String[] nameAndValue = header.split(":");
                return new BasicHeader(nameAndValue[0], nameAndValue[1]);
            })
            .toArray(Header[]::new);
    }
}

