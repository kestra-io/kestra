package io.kestra.core.http.client;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.apache.FailedResponseInterceptor;
import io.kestra.core.http.client.apache.LoggingRequestInterceptor;
import io.kestra.core.http.client.apache.LoggingResponseInterceptor;
import io.kestra.core.http.client.apache.RunContextResponseInterceptor;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.RetryUtils;
import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.binder.httpcomponents.hc5.ApacheHttpClientContext;
import io.micrometer.core.instrument.binder.httpcomponents.hc5.DefaultApacheHttpClientObservationConvention;
import io.micrometer.core.instrument.binder.httpcomponents.hc5.ObservationExecChainHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micronaut.http.MediaType;
import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.ContextBuilder;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.ChainElement;
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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class HttpClient implements Closeable {
    private transient CloseableHttpClient client;
    private final RunContext runContext;
    private final HttpConfiguration configuration;
    private ObservationRegistry observationRegistry;

    @Builder
    public HttpClient(RunContext runContext, @Nullable HttpConfiguration configuration) throws IllegalVariableEvaluationException {
        this.runContext = runContext;
        this.configuration = configuration == null ? HttpConfiguration.builder().build() : configuration;
        if (runContext instanceof DefaultRunContext defaultRunContext) {
            this.observationRegistry = defaultRunContext.getApplicationContext().findBean(ObservationRegistry.class).orElse(null);
        }

        this.client = this.createClient();
    }

    private CloseableHttpClient createClient() throws IllegalVariableEvaluationException {
        if (this.client != null) {
            throw new IllegalStateException("Client has already been created");
        }

        var builder = HttpClients.custom()
            .disableDefaultUserAgent()
            .setUserAgent("Kestra");

        if (observationRegistry != null) {
            // micrometer, must be placed before the retry strategy (see https://docs.micrometer.io/micrometer/reference/reference/httpcomponents.html#_retry_strategy_considerations)
            builder.addExecInterceptorAfter(ChainElement.RETRY.name(), "micrometer",
                new ObservationExecChainHandler(observationRegistry, new CustomApacheHttpClientObservationConvention())
            );
        }

        // logger
        if (this.configuration.getLogs() != null && this.configuration.getLogs().length > 0) {
            if (ArrayUtils.contains(this.configuration.getLogs(), HttpConfiguration.LoggingType.REQUEST_HEADERS) ||
                ArrayUtils.contains(this.configuration.getLogs(), HttpConfiguration.LoggingType.REQUEST_BODY)) {
                builder.addRequestInterceptorLast(new LoggingRequestInterceptor(runContext.logger(), this.configuration.getLogs()));
            }
            if (ArrayUtils.contains(this.configuration.getLogs(), HttpConfiguration.LoggingType.RESPONSE_HEADERS) ||
                ArrayUtils.contains(this.configuration.getLogs(), HttpConfiguration.LoggingType.RESPONSE_BODY)) {
                builder.addResponseInterceptorLast(new LoggingResponseInterceptor(runContext.logger(), this.configuration.getLogs()));
            }
        }

        var connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create();
        var connectionConfig = ConnectionConfig.custom();
        var credentialsStore = new BasicCredentialsProvider();

        if (this.configuration.getTimeout() != null) {
            var connectTimeout = runContext.render(this.configuration.getTimeout().getConnectTimeout()).as(java.time.Duration.class);
            connectTimeout.ifPresent(duration -> connectionConfig.setConnectTimeout(Timeout.of(duration)));
            var readIdleTimeout = runContext.render(this.configuration.getTimeout().getReadIdleTimeout()).as(java.time.Duration.class);
            readIdleTimeout.ifPresent(duration -> connectionConfig.setSocketTimeout(Timeout.of(duration)));
        }

        if (this.configuration.getProxy() != null && configuration.getProxy().getAddress() != null) {
            var proxyAddress = runContext.render(configuration.getProxy().getAddress()).as(String.class).orElse(null);

            if (StringUtils.isNotEmpty(proxyAddress)) {
                var port = runContext.render(configuration.getProxy().getPort()).as(Integer.class).orElseThrow();
                var proxyAddr = new InetSocketAddress(proxyAddress, port);
                var proxy = new Proxy(runContext.render(configuration.getProxy().getType()).as(Proxy.Type.class).orElse(null), proxyAddr);

                builder.setProxySelector(new ProxySelector() {
                    @Override
                    public void connectFailed(URI uri, SocketAddress sa, IOException e) {
                    }

                    @Override
                    public List<Proxy> select(URI uri) {
                        return List.of(proxy);
                    }
                });

                if (this.configuration.getProxy().getUsername() != null && this.configuration.getProxy().getPassword() != null) {
                    builder.setProxyAuthenticationStrategy(new DefaultAuthenticationStrategy());
                    credentialsStore.setCredentials(
                        new AuthScope(proxyAddress, port),
                        new UsernamePasswordCredentials(
                            runContext.render(this.configuration.getProxy().getUsername()).as(String.class).orElseThrow(),
                            runContext.render(this.configuration.getProxy().getPassword()).as(String.class).orElseThrow().toCharArray()
                        )
                    );
                }
            }
        }

        if (this.configuration.getSsl() != null) {
            if (this.configuration.getSsl().getInsecureTrustAllCertificates() != null) {
                connectionManagerBuilder.setSSLSocketFactory(this.selfSignedConnectionSocketFactory());
            }
        }

        if (this.configuration.getAuth() != null) {
            this.configuration.getAuth().configure(builder, runContext);
        }

        if (!runContext.render(this.configuration.getFollowRedirects()).as(Boolean.class).orElseThrow()) {
            builder.disableRedirectHandling();
        }

        if (!runContext.render(this.configuration.getAllowFailed()).as(Boolean.class).orElseThrow()) {
            builder.addResponseInterceptorLast(new FailedResponseInterceptor());
        }

        if (this.configuration.getAllowedResponseCodes() != null) {
            var list = runContext.render(this.configuration.getAllowedResponseCodes()).asList(Integer.class);
            if (!list.isEmpty()) {
                builder.addResponseInterceptorLast(new FailedResponseInterceptor(list));
            }
        }

        builder.addResponseInterceptorLast(new RunContextResponseInterceptor(this.runContext));

        connectionManagerBuilder.setDefaultConnectionConfig(connectionConfig.build());
        builder.setConnectionManager(connectionManagerBuilder.build());
        builder.setDefaultCredentialsProvider(credentialsStore);

        this.client = builder.build();
        return client;
    }

    private SSLConnectionSocketFactory selfSignedConnectionSocketFactory() {
        try {
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, (chain, authType) -> true).build();
            return new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public <T> HttpResponse<T> request(HttpRequest request, Class<T> cls) throws HttpClientException, IllegalVariableEvaluationException {
        var httpClientContext = this.clientContext(request);
        return this.request(request, httpClientContext, r -> {
            T body = bodyHandler(cls, r.getEntity());
            return HttpResponse.from(r, body, request, httpClientContext);
        });
    }

    public HttpResponse<Void> request(HttpRequest request, Consumer<HttpResponse<InputStream>> consumer) throws HttpClientException, IllegalVariableEvaluationException {
        var httpClientContext = this.clientContext(request);
        return this.request(request, httpClientContext, r -> {
            var from = HttpResponse.from(r, r.getEntity() != null ? r.getEntity().getContent() : null, request, httpClientContext);
            consumer.accept(from);
            return HttpResponse.from(r, null, request, httpClientContext);
        });
    }

    public <T> HttpResponse<T> request(HttpRequest request) throws HttpClientException, IllegalVariableEvaluationException {
        var httpClientContext = this.clientContext(request);
        return this.request(request, httpClientContext, response -> {
            T body = JacksonMapper.ofJson().readValue(response.getEntity().getContent(), new TypeReference<>() {
            });
            return HttpResponse.from(response, body, request, httpClientContext);
        });
    }

    private HttpClientContext clientContext(HttpRequest request) {
        var contextBuilder = ContextBuilder.create();
        return contextBuilder.build();
    }

    private <T> HttpResponse<T> request(
        HttpRequest request,
        HttpClientContext httpClientContext,
        HttpClientResponseHandler<HttpResponse<T>> responseHandler
    ) throws HttpClientException, IllegalVariableEvaluationException {

        var retryableCodes = runContext.render(configuration.getRetryOnStatusCodes()).asList(Integer.class);

        return new RetryUtils().<HttpResponse<T>, HttpClientException>of(configuration.getRetry())
            .run(
                (res, throwable) -> {
                    if (throwable instanceof HttpClientResponseException ex) {
                        return retryableCodes.contains(ex.getResponse().getStatus().getCode());
                    }
                    return throwable instanceof HttpClientRequestException
                        || throwable instanceof SocketException
                        || throwable instanceof SSLHandshakeException;
                },
                () -> {
                    try {
                        return this.client.execute(request.to(runContext), httpClientContext, responseHandler);
                    } catch (SocketException | SSLHandshakeException e) {
                        throw new HttpClientRequestException(e.getMessage(), request, e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            );
    }

    @SuppressWarnings("unchecked")
    private <T> T bodyHandler(Class<?> cls, HttpEntity entity) throws IOException, ParseException {
        if (entity == null) {
            return null;
        } else if (String.class.isAssignableFrom(cls)) {
            return (T) EntityUtils.toString(entity);
        } else if (Byte[].class.isAssignableFrom(cls)) {
            return (T) ArrayUtils.toObject(EntityUtils.toByteArray(entity));
        } else if (MediaType.APPLICATION_YAML.equals(entity.getContentType()) || "application/yaml".equals(entity.getContentType())) {
            return (T) JacksonMapper.ofYaml().readValue(entity.getContent(), cls);
        } else {
            return (T) JacksonMapper.ofJson(false).readValue(entity.getContent(), cls);
        }
    }

    @Override
    public void close() throws IOException {
        if (this.client != null) {
            this.client.close();
        }
    }

    public static class CustomApacheHttpClientObservationConvention extends DefaultApacheHttpClientObservationConvention {
        @Override
        public KeyValues getLowCardinalityKeyValues(ApacheHttpClientContext context) {
            return KeyValues.concat(
                super.getLowCardinalityKeyValues(context),
                KeyValues.of("type", "core-client")
            );
        }
    }
}
