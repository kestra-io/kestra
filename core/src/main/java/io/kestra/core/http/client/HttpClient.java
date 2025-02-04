package io.kestra.core.http.client;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.apache.*;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.hc.client5.http.ContextBuilder;
import org.apache.hc.client5.http.auth.*;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;

@Slf4j
public class HttpClient implements Closeable {
    private transient CloseableHttpClient client;
    private final RunContext runContext;
    private final HttpConfiguration configuration;

    @Builder
    public HttpClient(RunContext runContext, @Nullable HttpConfiguration configuration) throws IllegalVariableEvaluationException {
        this.runContext = runContext;
        this.configuration = configuration == null ? HttpConfiguration.builder().build() : configuration;
        this.client = this.createClient();
    }

    private CloseableHttpClient createClient() throws IllegalVariableEvaluationException {
        if (this.client != null) {
            throw new IllegalStateException("Client has already been created");
        }

        org.apache.hc.client5.http.impl.classic.HttpClientBuilder builder = HttpClients.custom()
            .disableDefaultUserAgent()
            .setUserAgent("Kestra");

        // logger
        if (this.configuration.getLogs() != null && this.configuration.getLogs().length > 0) {
            if (ArrayUtils.contains(this.configuration.getLogs(), HttpConfiguration.LoggingType.REQUEST_HEADERS) ||
                ArrayUtils.contains(this.configuration.getLogs(), HttpConfiguration.LoggingType.REQUEST_BODY)
            ) {
                builder.addRequestInterceptorLast(new LoggingRequestInterceptor(runContext.logger(), this.configuration.getLogs()));
            }

            if (ArrayUtils.contains(this.configuration.getLogs(), HttpConfiguration.LoggingType.RESPONSE_HEADERS) ||
                ArrayUtils.contains(this.configuration.getLogs(), HttpConfiguration.LoggingType.RESPONSE_BODY)
            ) {
                builder.addResponseInterceptorLast(new LoggingResponseInterceptor(runContext.logger(), this.configuration.getLogs()));
            }
        }

        // Object dependencies
        PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create();
        ConnectionConfig.Builder connectionConfig = ConnectionConfig.custom();
        BasicCredentialsProvider credentialsStore = new BasicCredentialsProvider();

        // Timeout
        if (this.configuration.getTimeout() != null) {
            var connectTiemout = runContext.render(this.configuration.getTimeout().getConnectTimeout()).as(Duration.class);
            connectTiemout.ifPresent(duration -> connectionConfig.setConnectTimeout(Timeout.of(duration)));

            var readIdleTiemout = runContext.render(this.configuration.getTimeout().getReadIdleTimeout()).as(Duration.class);
            readIdleTiemout.ifPresent(duration -> connectionConfig.setSocketTimeout(Timeout.of(duration)));
        }

        // proxy
        if (this.configuration.getProxy() != null) {
            SocketAddress proxyAddr = new InetSocketAddress(
                runContext.render(configuration.getProxy().getAddress()).as(String.class).orElse(null),
                runContext.render(configuration.getProxy().getPort()).as(Integer.class).orElse(null)
            );

            Proxy proxy = new Proxy(runContext.render(configuration.getProxy().getType()).as(Proxy.Type.class).orElse(null), proxyAddr);

            builder.setProxySelector(new ProxySelector() {
                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException e) {
                    /* ignore */
                }

                @Override
                public List<Proxy> select(URI uri) {
                    return List.of(proxy);
                }
            });

            if (this.configuration.getProxy().getUsername() != null && this.configuration.getProxy().getPassword() != null) {
                builder.setProxyAuthenticationStrategy(new DefaultAuthenticationStrategy());

                credentialsStore.setCredentials(
                    new AuthScope(
                        runContext.render(this.configuration.getProxy().getAddress()).as(String.class).orElse(null),
                        runContext.render(this.configuration.getProxy().getPort()).as(Integer.class).orElse(null)
                    ),
                    new UsernamePasswordCredentials(
                        runContext.render(this.configuration.getProxy().getUsername()).as(String.class).orElseThrow(),
                        runContext.render(this.configuration.getProxy().getPassword()).as(String.class).orElseThrow().toCharArray()
                    )
                );
            }
        }

        // ssl
        if (this.configuration.getSsl() != null) {
            if (this.configuration.getSsl().getInsecureTrustAllCertificates() != null) {
                connectionManagerBuilder.setSSLSocketFactory(this.selfSignedConnectionSocketFactory());
            }
        }

        // auth
        if (this.configuration.getAuth() != null) {
            this.configuration.getAuth().configure(builder, runContext);
        }

        // root options
        if (!runContext.render(this.configuration.getFollowRedirects()).as(Boolean.class).orElseThrow()) {
            builder.disableRedirectHandling();
        }

        if (!runContext.render(this.configuration.getAllowFailed()).as(Boolean.class).orElseThrow()) {
            builder.addResponseInterceptorLast(new FailedResponseInterceptor());
        }

        builder.addResponseInterceptorLast(new RunContextResponseInterceptor(this.runContext));

        // builder object
        connectionManagerBuilder.setDefaultConnectionConfig(connectionConfig.build());
        builder.setConnectionManager(connectionManagerBuilder.build());
        builder.setDefaultCredentialsProvider(credentialsStore);

        this.client = builder.build();

        return client;
    }

    private SSLConnectionSocketFactory selfSignedConnectionSocketFactory() {
        try {
            SSLContext sslContext = SSLContexts
                .custom()
                .loadTrustMaterial(null, (chain, authType) -> true)
                .build();

            return new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Send a request
     *
     * @param request the request
     * @param cls the class of the response
     * @param <T> the type of response expected
     * @return the response
     */
    public <T> HttpResponse<T> request(HttpRequest request, Class<T> cls) throws HttpClientException, IllegalVariableEvaluationException {
        HttpClientContext httpClientContext = this.clientContext(request);

        return this.request(request, httpClientContext, r -> {
            T body = bodyHandler(cls, r.getEntity());

            return HttpResponse.from(r, body, request, httpClientContext);
        });
    }

    /**
     * Send a request, getting the response with body as input stream
     *
     * @param request the request
     * @param consumer the consumer of the response
     * @return the response without the body
     */
    public HttpResponse<Void> request(HttpRequest request, Consumer<HttpResponse<InputStream>> consumer) throws HttpClientException, IllegalVariableEvaluationException {
        HttpClientContext httpClientContext = this.clientContext(request);

        return this.request(request, httpClientContext, r -> {
            HttpResponse<InputStream> from = HttpResponse.from(
                r,
                r.getEntity() != null ? r.getEntity().getContent() : null,
                request,
                httpClientContext
            );

            consumer.accept(from);

            return HttpResponse.from(r, null, request, httpClientContext);
        });
    }

    /**
     * Send a request and expect a json response
     *
     * @param request the request
     * @param <T> the type of response expected
     * @return the response
     */
    public <T> HttpResponse<T> request(HttpRequest request) throws HttpClientException, IllegalVariableEvaluationException {
        HttpClientContext httpClientContext = this.clientContext(request);

        return this.request(request, httpClientContext, response -> {
            T body = JacksonMapper.ofJson().readValue(response.getEntity().getContent(), new TypeReference<>() {});

            return HttpResponse.from(response, body, request, httpClientContext);
        });
    }

    private HttpClientContext clientContext(HttpRequest request) {
        ContextBuilder contextBuilder = ContextBuilder.create();

        return contextBuilder.build();
    }

    private <T> HttpResponse<T> request(
        HttpRequest request,
        HttpClientContext httpClientContext,
        HttpClientResponseHandler<HttpResponse<T>> responseHandler
    ) throws HttpClientException {
        try {
            return this.client.execute(request.to(runContext), httpClientContext, responseHandler);
        } catch (SocketException e) {
            throw new HttpClientRequestException(e.getMessage(), request, e);
        } catch (IOException e) {
            if (e instanceof SSLHandshakeException) {
                throw new HttpClientRequestException(e.getMessage(), request, e);
            }

            if (e.getCause() instanceof HttpClientException httpClientException) {
                throw httpClientException;
            }

            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T bodyHandler(Class<?> cls, HttpEntity entity) throws IOException, ParseException {
        if (entity == null) {
            return null;
        } else if (cls.isAssignableFrom(String.class)) {
            return (T) EntityUtils.toString(entity);
        } else if (cls.isAssignableFrom(Byte[].class)) {
            return (T) ArrayUtils.toObject(EntityUtils.toByteArray(entity));
        } else {
            return (T) JacksonMapper.ofJson().readValue(entity.getContent(), cls);
        }
    }

    @Override
    public void close() throws IOException {
        if (this.client != null) {
            this.client.close();
        }
    }
}
