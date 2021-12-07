package io.kestra.repository.elasticsearch.configs;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;

import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.SSLContext;

@ConfigurationProperties("kestra.elasticsearch.client")
@Getter
public class ElasticsearchConfig {
    String[] httpHosts;
    String[] defaultHeaders;
    String pathPrefix;
    Boolean strictDeprecationMode;
    Boolean trustAllSsl;

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

    @SneakyThrows
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

        if (trustAllSsl != null && trustAllSsl) {
            SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
            sslContextBuilder.loadTrustMaterial(null, (TrustStrategy) (chain, authType) -> true);
            SSLContext sslContext = sslContextBuilder.build();

            builder.setSSLContext(sslContext);
            builder.setSSLHostnameVerifier(new NoopHostnameVerifier());
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

