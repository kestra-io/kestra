package io.kestra.core.http.client;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.ContextBuilder;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.auth.*;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.nio.support.classic.TransportException;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.HttpService;
import io.kestra.core.http.HttpSseEvent;
import io.kestra.core.http.KestraMediaTypes;
import io.kestra.core.http.client.apache.*;
import io.kestra.core.http.client.configurations.DigestAuthConfiguration;
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

@Slf4j
public class HttpClient implements Closeable {
    /**
     * Matches a {@code network/prefixLength} allow/deny-list entry, e.g. {@code 169.254.0.0/16}.
     */
    private static final Pattern CIDR_ENTRY = Pattern.compile("^(?:(https?)://)?([^/]+)/(\\d{1,3})$");

    private transient CloseableHttpClient client;
    private transient BasicCredentialsProvider defaultCredentialsProvider;
    private boolean hasProxy;
    private final RunContext runContext;
    private final HttpConfiguration configuration;
    private ObservationRegistry observationRegistry;
    private static final Set<String> IDEMPOTENT_METHODS = Set.of("GET", "HEAD");

    // The read timeout the classic client gets from Apache when none is configured, which the async client lacks.
    private static final Timeout DEFAULT_READ_IDLE_TIMEOUT = Timeout.ofMinutes(3);

    // Offers only gzip/deflate so httpclient5 never negotiates Brotli, whose native brotli4j library may be missing.
    private static final HttpRequestInterceptor ACCEPT_ENCODING_INTERCEPTOR = (request, entity, context) -> request.setHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, x-gzip, deflate");

    @Builder
    public HttpClient(RunContext runContext, @Nullable HttpConfiguration configuration) throws IllegalVariableEvaluationException {
        this.runContext = runContext;
        this.configuration = configuration == null ? HttpConfiguration.builder().build() : configuration;
        if (runContext instanceof DefaultRunContext defaultRunContext) {
            this.observationRegistry = defaultRunContext.services().observationRegistry().orElse(null);
        }

        this.client = this.createClient();
    }

    private CloseableHttpClient createClient() throws IllegalVariableEvaluationException {
        if (this.client != null) {
            throw new IllegalStateException("Client has already been created");
        }

        HttpConfiguration.HttpVersion httpVersion = runContext.render(this.configuration.getHttpVersion())
            .as(HttpConfiguration.HttpVersion.class)
            .orElse(HttpConfiguration.HttpVersion.HTTP_1_1);

        return httpVersion == HttpConfiguration.HttpVersion.HTTP_1_1 ? this.createClassicClient() : this.createAsyncClient(httpVersion);
    }

    private CloseableHttpClient createClassicClient() throws IllegalVariableEvaluationException {
        org.apache.hc.client5.http.impl.classic.HttpClientBuilder builder = HttpClients.custom()
            .disableDefaultUserAgent()
            .setUserAgent("Kestra");

        builder.addRequestInterceptorFirst(ACCEPT_ENCODING_INTERCEPTOR);

        // Positioned right after REDIRECT so it re-runs on every hop RedirectExec follows, unlike a request interceptor which only runs once before the redirect loop starts.
        builder.addExecInterceptorAfter(
            ChainElement.REDIRECT.name(), "ssrf-guard",
            (request, scope, chain) ->
            {
                validateUri(HttpService.safeURI(request));
                return chain.proceed(request, scope);
            }
        );

        if (observationRegistry != null) {
            // micrometer, must be placed before the retry strategy (see https://docs.micrometer.io/micrometer/reference/reference/httpcomponents.html#_retry_strategy_considerations)
            builder.addExecInterceptorAfter(
                ChainElement.RETRY.name(), "micrometer",
                new ObservationExecChainHandler(observationRegistry, new CustomApacheHttpClientObservationConvention())
            );
        }

        // logger
        HttpRequestInterceptor requestLogger = this.requestLogger();
        if (requestLogger != null) {
            builder.addRequestInterceptorLast(requestLogger);
        }

        HttpResponseInterceptor responseLogger = this.responseLogger();
        if (responseLogger != null) {
            builder.addResponseInterceptorLast(responseLogger);
        }

        // Object dependencies
        PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create();
        this.defaultCredentialsProvider = new BasicCredentialsProvider();

        // proxy
        ProxySelector proxySelector = this.proxySelector();
        if (proxySelector != null) {
            builder.setProxySelector(proxySelector);

            if (this.hasProxyCredentials()) {
                builder.setProxyAuthenticationStrategy(new DefaultAuthenticationStrategy());
            }
        }

        // Behind a proxy this resolver would only see the proxy host, which may legitimately sit in a denied range.
        if (!this.hasProxy) {
            connectionManagerBuilder.setDnsResolver(new DeniedAddressDnsResolver());
        }

        // ssl
        if (this.insecureTrustAllCertificates()) {
            connectionManagerBuilder.setSSLSocketFactory(this.selfSignedConnectionSocketFactory());
        }

        // auth
        if (this.configuration.getAuth() != null) {
            this.configuration.getAuth().configure(builder, runContext);
        }

        // root options
        if (!runContext.render(this.configuration.getFollowRedirects()).as(Boolean.class).orElseThrow()) {
            builder.disableRedirectHandling();
        }

        builder.addResponseInterceptorLast(new RunContextResponseInterceptor(this.runContext));

        // TCP Keep-Alive extended socket options (disabled for Windows compatibility)
        if (!this.tcpExtendedKeepAliveEnabled()) {
            connectionManagerBuilder.setDefaultSocketConfig(
                SocketConfig.custom()
                    .setTcpKeepIdle(0)
                    .setTcpKeepInterval(0)
                    .setTcpKeepCount(0)
                    .build()
            );
        }

        // builder object
        connectionManagerBuilder.setDefaultConnectionConfig(this.connectionConfig());
        builder.setConnectionManager(connectionManagerBuilder.build());
        builder.setDefaultCredentialsProvider(this.defaultCredentialsProvider);

        this.client = builder.build();

        return client;
    }

    private SSLConnectionSocketFactory selfSignedConnectionSocketFactory() {
        return new SSLConnectionSocketFactory(this.insecureSslContext(), new String[] {"TLSv1.3", "TLSv1.2"}, null, NoopHostnameVerifier.INSTANCE);
    }

    private SSLContext insecureSslContext() {
        try {
            return SSLContexts
                .custom()
                .loadTrustMaterial(null, (chain, authType) -> true)
                .build();
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private boolean insecureTrustAllCertificates() {
        return this.configuration.getSsl() != null && this.configuration.getSsl().getInsecureTrustAllCertificates() != null;
    }

    private boolean tcpExtendedKeepAliveEnabled() throws IllegalVariableEvaluationException {
        return runContext.render(this.configuration.getEnabledTcpExtendedKeepAlive()).as(Boolean.class).orElse(true);
    }

    private HttpRequestInterceptor requestLogger() {
        HttpConfiguration.LoggingType[] logs = this.configuration.getLogs();
        if (logs != null && (ArrayUtils.contains(logs, HttpConfiguration.LoggingType.REQUEST_HEADERS) || ArrayUtils.contains(logs, HttpConfiguration.LoggingType.REQUEST_BODY))) {
            return new LoggingRequestInterceptor(runContext.logger(), logs);
        }

        return null;
    }

    private HttpResponseInterceptor responseLogger() {
        HttpConfiguration.LoggingType[] logs = this.configuration.getLogs();
        if (logs != null && (ArrayUtils.contains(logs, HttpConfiguration.LoggingType.RESPONSE_HEADERS) || ArrayUtils.contains(logs, HttpConfiguration.LoggingType.RESPONSE_BODY))) {
            return new LoggingResponseInterceptor(runContext.logger(), logs);
        }

        return null;
    }

    private ConnectionConfig connectionConfig() throws IllegalVariableEvaluationException {
        ConnectionConfig.Builder connectionConfig = ConnectionConfig.custom();

        if (this.configuration.getTimeout() != null) {
            var connectTimeout = runContext.render(this.configuration.getTimeout().getConnectTimeout()).as(Duration.class);
            connectTimeout.ifPresent(duration -> connectionConfig.setConnectTimeout(Timeout.of(duration)));

            var readIdleTimeout = runContext.render(this.configuration.getTimeout().getReadIdleTimeout()).as(Duration.class);
            readIdleTimeout.ifPresent(duration -> connectionConfig.setSocketTimeout(Timeout.of(duration)));
        }

        return connectionConfig.build();
    }

    private ProxySelector proxySelector() throws IllegalVariableEvaluationException {
        if (this.configuration.getProxy() == null || configuration.getProxy().getAddress() == null) {
            return null;
        }

        String proxyAddress = runContext.render(configuration.getProxy().getAddress()).as(String.class).orElse(null);
        if (StringUtils.isEmpty(proxyAddress)) {
            return null;
        }

        this.hasProxy = true;
        int port = runContext.render(configuration.getProxy().getPort()).as(Integer.class)
            .orElseThrow(() -> new IllegalArgumentException("A proxy port is required when a proxy address is set (options.proxy.port)."));
        SocketAddress proxyAddr = new InetSocketAddress(
            proxyAddress,
            port
        );

        Proxy proxy = new Proxy(runContext.render(configuration.getProxy().getType()).as(Proxy.Type.class).orElse(null), proxyAddr);

        if (this.hasProxyCredentials()) {
            this.defaultCredentialsProvider.setCredentials(
                new AuthScope(proxyAddress, port),
                new UsernamePasswordCredentials(
                    runContext.render(this.configuration.getProxy().getUsername()).as(String.class).orElseThrow(),
                    runContext.render(this.configuration.getProxy().getPassword()).as(String.class).orElseThrow().toCharArray()
                )
            );
        }

        return new ProxySelector() {
            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException e) {
                /* ignore */
            }

            @Override
            public List<Proxy> select(URI uri) {
                return List.of(proxy);
            }
        };
    }

    private boolean hasProxyCredentials() {
        return this.configuration.getProxy().getUsername() != null && this.configuration.getProxy().getPassword() != null;
    }

    private CloseableHttpClient createAsyncClient(HttpConfiguration.HttpVersion httpVersion) throws IllegalVariableEvaluationException {
        HttpAsyncClientBuilder builder = HttpAsyncClients.custom()
            .setUserAgent("Kestra")
            .setH2Config(H2Config.custom().setPushEnabled(false).build());

        builder.addRequestInterceptorFirst(ACCEPT_ENCODING_INTERCEPTOR);

        builder.addExecInterceptorAfter(
            ChainElement.REDIRECT.name(), "ssrf-guard",
            (request, entityProducer, scope, chain, asyncExecCallback) ->
            {
                try {
                    validateUri(HttpService.safeURI(request));
                } catch (RuntimeException e) {
                    // On a redirect hop this runs in an I/O callback, where a thrown exception would leave the request pending forever.
                    asyncExecCallback.failed(e);
                    return;
                }

                chain.proceed(request, entityProducer, scope, asyncExecCallback);
            }
        );

        if (observationRegistry != null) {
            builder.addExecInterceptorAfter(
                ChainElement.RETRY.name(), "micrometer",
                new ObservationExecChainHandler(observationRegistry, new CustomApacheHttpClientObservationConvention())
            );
        }

        HttpRequestInterceptor requestLogger = this.requestLogger();
        if (requestLogger != null) {
            builder.addRequestInterceptorLast(requestLogger);
        }

        HttpResponseInterceptor responseLogger = this.responseLogger();
        if (responseLogger != null) {
            builder.addResponseInterceptorLast(responseLogger);
        }

        // The async client gives the logging interceptors no readable body.
        if (
            ArrayUtils.contains(this.configuration.getLogs(), HttpConfiguration.LoggingType.REQUEST_BODY)
                || ArrayUtils.contains(this.configuration.getLogs(), HttpConfiguration.LoggingType.RESPONSE_BODY)
        ) {
            runContext.logger().warn("The request and response bodies cannot be logged with the HTTP version {}, only the headers are.", httpVersion);
        }

        PoolingAsyncClientConnectionManagerBuilder connectionManagerBuilder = PoolingAsyncClientConnectionManagerBuilder.create();
        this.defaultCredentialsProvider = new BasicCredentialsProvider();

        ProxySelector proxySelector = this.proxySelector();
        if (proxySelector != null) {
            builder.setProxySelector(proxySelector);

            if (this.hasProxyCredentials()) {
                builder.setProxyAuthenticationStrategy(new DefaultAuthenticationStrategy());
            }
        }

        // Behind a proxy this resolver would only see the proxy host, which may legitimately sit in a denied range.
        if (!this.hasProxy) {
            connectionManagerBuilder.setDnsResolver(new DeniedAddressDnsResolver());
        }

        connectionManagerBuilder.setDefaultTlsConfig(
            TlsConfig.custom()
                .setVersionPolicy(httpVersion == HttpConfiguration.HttpVersion.HTTP_2 ? HttpVersionPolicy.FORCE_HTTP_2 : HttpVersionPolicy.NEGOTIATE)
                .build()
        );

        if (this.insecureTrustAllCertificates()) {
            connectionManagerBuilder.setTlsStrategy(
                ClientTlsStrategyBuilder.create()
                    .setSslContext(this.insecureSslContext())
                    .setTlsVersions(TLS.V_1_3, TLS.V_1_2)
                    .setHostVerificationPolicy(HostnameVerificationPolicy.CLIENT)
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .buildAsync()
            );
        }

        if (this.configuration.getAuth() != null) {
            this.configuration.getAuth().configure(builder, runContext);
        }

        if (!runContext.render(this.configuration.getFollowRedirects()).as(Boolean.class).orElseThrow()) {
            builder.disableRedirectHandling();
        }

        builder.addResponseInterceptorLast(new RunContextResponseInterceptor(this.runContext));

        // A client serves a single task, so one I/O thread is enough instead of one per CPU core.
        IOReactorConfig.Builder ioReactorConfig = IOReactorConfig.custom().setIoThreadCount(1);

        // TCP Keep-Alive extended socket options (disabled for Windows compatibility)
        if (!this.tcpExtendedKeepAliveEnabled()) {
            ioReactorConfig
                .setTcpKeepIdle(0)
                .setTcpKeepInterval(0)
                .setTcpKeepCount(0);
        }

        builder.setIOReactorConfig(ioReactorConfig.build());

        ConnectionConfig connectionConfig = this.connectionConfig();
        Timeout readIdleTimeout = connectionConfig.getSocketTimeout() == null ? DEFAULT_READ_IDLE_TIMEOUT : connectionConfig.getSocketTimeout();

        connectionManagerBuilder.setDefaultConnectionConfig(ConnectionConfig.copy(connectionConfig).setSocketTimeout(readIdleTimeout).build());
        builder.setConnectionManager(connectionManagerBuilder.build());
        builder.setDefaultCredentialsProvider(this.defaultCredentialsProvider);

        CloseableHttpAsyncClient asyncClient = builder.build();
        asyncClient.start();

        return HttpAsyncClients.classic(asyncClient, readIdleTimeout);
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
        boolean allowFailed = runContext.render(this.configuration.getAllowFailed()).as(Boolean.class).orElseThrow();
        List<Integer> allowedResponseCodes = this.configuration.getAllowedResponseCodes() != null ? runContext.render(this.configuration.getAllowedResponseCodes()).asList(Integer.class)
            : null;
        HttpClientContext httpClientContext = this.clientContext(request);

        return this.request(request, httpClientContext, r ->
        {
            this.throwIfResponseNotAllowed(r, httpClientContext, allowFailed, allowedResponseCodes);
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
        boolean allowFailed = runContext.render(this.configuration.getAllowFailed()).as(Boolean.class).orElseThrow();
        List<Integer> allowedResponseCodes = this.configuration.getAllowedResponseCodes() != null ? runContext.render(this.configuration.getAllowedResponseCodes()).asList(Integer.class)
            : null;
        HttpClientContext httpClientContext = this.clientContext(request);

        return this.request(request, httpClientContext, r ->
        {
            this.throwIfResponseNotAllowed(r, httpClientContext, allowFailed, allowedResponseCodes);
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
        boolean allowFailed = runContext.render(this.configuration.getAllowFailed()).as(Boolean.class).orElseThrow();
        List<Integer> allowedResponseCodes = this.configuration.getAllowedResponseCodes() != null ? runContext.render(this.configuration.getAllowedResponseCodes()).asList(Integer.class)
            : null;
        HttpClientContext httpClientContext = this.clientContext(request);

        return this.request(request, httpClientContext, response ->
        {
            this.throwIfResponseNotAllowed(response, httpClientContext, allowFailed, allowedResponseCodes);
            T body = JacksonMapper.ofJson().readValue(response.getEntity().getContent(), new TypeReference<>() {
            });

            return HttpResponse.from(response, body, request, httpClientContext);
        });
    }

    /**
     * Send an SSE (Server-Sent Events) request and consume events with typed data.
     *
     * @param request the HTTP request
     * @param cls the class type for deserializing event data
     * @param eventConsumer consumer that processes each SSE event with typed data
     * @param <T> the type of data in the SSE events
     * @return the HTTP response without the body, as events are consumed through the eventConsumer
     */
    public <T> HttpResponse<Void> sseRequest(
        HttpRequest request,
        Class<T> cls,
        Consumer<HttpSseEvent<T>> eventConsumer) throws HttpClientException, IllegalVariableEvaluationException {
        HttpClientContext httpClientContext = this.clientContext(request);

        HttpClientResponseHandler<HttpResponse<Void>> responseHandler = response ->
        {

            parseSse(response.getEntity().getContent(), cls, eventConsumer);

            return HttpResponse.from(response, null, request, httpClientContext);
        };

        return this.request(request, httpClientContext, responseHandler);
    }

    private <T> void parseSse(InputStream inputStream, Class<T> cls, Consumer<HttpSseEvent<T>> eventConsumer) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder dataBuffer = new StringBuilder();
            boolean hasData = false;
            String eventId = null;
            String eventName = null;
            String comment = null;
            Duration retry = null;

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    // Empty line: dispatch event if data was provided
                    if (hasData) {
                        // Per spec: remove the trailing newline from the data buffer
                        if (!dataBuffer.isEmpty() && dataBuffer.charAt(dataBuffer.length() - 1) == '\n') {
                            dataBuffer.setLength(dataBuffer.length() - 1);
                        }
                        sendSseData(cls, eventConsumer, dataBuffer, eventId, eventName, comment, retry);
                    }

                    // Reset for next event (even if no data was present)
                    dataBuffer.setLength(0);
                    hasData = false;
                    eventId = null;
                    eventName = null;
                    comment = null;
                    retry = null;
                    continue;
                }

                if (line.startsWith(":")) {
                    // Comment line - entire line starts with colon
                    comment = stripLeadingSpace(line.substring(1));
                    continue;
                }

                // Parse field name and value
                String fieldName;
                String fieldValue;
                int colonIndex = line.indexOf(':');
                if (colonIndex >= 0) {
                    fieldName = line.substring(0, colonIndex);
                    // Per spec: strip only a single leading space after the colon
                    fieldValue = stripLeadingSpace(line.substring(colonIndex + 1));
                } else {
                    // No colon: entire line is the field name, value is empty string
                    fieldName = line;
                    fieldValue = "";
                }

                switch (fieldName) {
                    case "data" -> {
                        hasData = true;
                        // Per spec: append value + newline to data buffer
                        dataBuffer.append(fieldValue).append('\n');
                    }
                    case "id" -> {
                        // Per spec: ignore if value contains NULL character
                        if (!fieldValue.contains("\0")) {
                            eventId = fieldValue;
                        }
                    }
                    case "event" -> eventName = fieldValue;
                    case "retry" -> {
                        // Per spec: only accept if value consists entirely of ASCII digits
                        if (!fieldValue.isEmpty() && fieldValue.chars().allMatch(c -> c >= '0' && c <= '9')) {
                            try {
                                retry = Duration.ofMillis(Long.parseLong(fieldValue));
                            } catch (NumberFormatException e) {
                                // Value overflows, ignore
                            }
                        }
                    }
                    default -> {
                        // Unknown field names are ignored per spec
                    }
                }
            }

            // Per spec: end of stream does NOT dispatch pending events
        }
    }

    /**
     * Strip a single leading U+0020 SPACE character, per SSE spec.
     */
    private static String stripLeadingSpace(String value) {
        if (!value.isEmpty() && value.charAt(0) == ' ') {
            return value.substring(1);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private <T> void sendSseData(
        Class<T> cls,
        Consumer<HttpSseEvent<T>> eventConsumer,
        StringBuilder dataBuffer,
        String eventId,
        String eventName,
        String comment,
        Duration retry) {
        String dataStr = dataBuffer.toString();
        T parsedData = null;

        if (!dataStr.isEmpty()) {
            try {
                StringEntity tempEntity = new StringEntity(dataStr, ContentType.APPLICATION_JSON);

                parsedData = bodyHandler(cls, tempEntity);
            } catch (Exception e) {
                if (String.class.isAssignableFrom(cls)) {
                    parsedData = (T) dataStr;
                } else {
                    runContext.logger().warn("Failed to parse SSE event data: {}", dataStr, e);
                }
            }
        }

        HttpSseEvent<T> event = HttpSseEvent.<T> builder()
            .data(parsedData)
            .id(eventId)
            .name(eventName)
            .comment(comment)
            .retry(retry)
            .build();

        if (eventConsumer != null) {
            eventConsumer.accept(event);
        }
    }

    private HttpClientContext clientContext(HttpRequest request) throws IllegalVariableEvaluationException {
        HttpClientContext httpClientContext = ContextBuilder.create().build();

        if (this.configuration.getAuth() instanceof DigestAuthConfiguration digestAuthConfiguration) {
            String username = runContext.render(digestAuthConfiguration.getUsername()).as(String.class).orElse(null);
            String password = runContext.render(digestAuthConfiguration.getPassword()).as(String.class).orElse(null);

            if (StringUtils.isEmpty(username) || password == null) {
                throw new IllegalArgumentException("Digest authentication requires both `username` and `password`.");
            }

            URI uri = request.getUri();
            if (uri == null || uri.getHost() == null) {
                throw new IllegalArgumentException("Digest authentication requires an absolute URI with a host.");
            }

            int port = uri.getPort() != -1 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
            AuthScope digestScope = new AuthScope(uri.getHost(), port);
            UsernamePasswordCredentials digestCredentials = new UsernamePasswordCredentials(username, password.toCharArray());

            httpClientContext.setCredentialsProvider((authScope, context) ->
            {
                if (digestScope.match(authScope) >= 0) {
                    return digestCredentials;
                }
                return this.defaultCredentialsProvider.getCredentials(authScope, context);
            });
        }

        return httpClientContext;
    }

    private void throwIfResponseNotAllowed(
        org.apache.hc.core5.http.HttpResponse response,
        HttpClientContext context,
        boolean allowFailed,
        List<Integer> allowedResponseCodes) throws IOException {
        if (isAllowedStatusCode(response.getCode(), allowFailed, allowedResponseCodes)) {
            return;
        }

        throw new IOException(HttpResponseFailure.exception(response, context));
    }

    private static boolean isAllowedStatusCode(int statusCode, boolean allowFailed, List<Integer> allowedResponseCodes) {
        if (allowedResponseCodes != null && !allowedResponseCodes.isEmpty()) {
            return allowedResponseCodes.contains(statusCode);
        }

        if (allowFailed) {
            return true;
        }

        return statusCode < 400;
    }

    private <T> HttpResponse<T> request(
        HttpRequest request,
        HttpClientContext httpClientContext,
        HttpClientResponseHandler<HttpResponse<T>> responseHandler) throws HttpClientException, IllegalVariableEvaluationException {
        validateUri(request.getUri());

        String method = request.getMethod();
        List<Integer> retryableCodes = resolveRetryableStatusCodes(method);
        boolean retryTransportFailures = isMethodEligibleForTransportRetry(method);

        return RetryUtils.<HttpResponse<T>, HttpClientException> of(
            configuration.getRetry(),
            retryFailed ->
            {
                if (retryFailed.getCause() instanceof HttpClientException ex) {
                    return ex;
                }

                throw new IllegalStateException(
                    "Retry failed with an unexpected exception type",
                    retryFailed.getCause()
                );
            }
        ).run(
            (res, throwable) ->
            {
                if (throwable instanceof HttpClientResponseException ex) {
                    return retryableCodes.contains(
                        ex.getResponse().getStatus().getCode()
                    );
                }

                if (throwable instanceof HttpClientRequestException httpEx) {
                    // ConnectException means the request never reached the server, so any method is safe to retry.
                    Throwable cause = httpEx.getCause();
                    if (cause instanceof SSLHandshakeException) {
                        return false;
                    }
                    return cause instanceof ConnectException || retryTransportFailures;
                }

                return false;
            },
            () ->
            {
                try {
                    return this.client.execute(
                        request.to(runContext),
                        httpClientContext,
                        responseHandler
                    );
                } catch (SocketException | SSLHandshakeException e) {
                    throw new HttpClientRequestException(
                        e.getMessage(),
                        request,
                        e
                    );
                } catch (IOException e) {
                    if (e.getCause() instanceof HttpClientException ex) {
                        throw ex;
                    }

                    // The async client wraps transport errors, which would otherwise not be retried as they are with the classic client.
                    if (e instanceof TransportException && (e.getCause() instanceof SocketException || e.getCause() instanceof SSLHandshakeException)) {
                        throw new HttpClientRequestException(
                            e.getCause().getMessage(),
                            request,
                            e.getCause()
                        );
                    }

                    throw new RuntimeException(e);
                }
            }
        );
    }

    /**
     * Resolves the retryable status codes for a given HTTP method: the method's entry in
     * {@code retryOnStatusCodesByMethod} if one is configured (matched case-insensitively), otherwise the
     * global {@code retryOnStatusCodes} for GET/HEAD methods only.
     */
    @SuppressWarnings("unchecked")
    private List<Integer> resolveRetryableStatusCodes(String method) throws IllegalVariableEvaluationException {
        if (configuration.getRetryOnStatusCodesByMethod() != null) {
            Map<String, List<Integer>> byMethod = runContext
                .render(configuration.getRetryOnStatusCodesByMethod())
                .asMap(String.class, List.class);

            for (Map.Entry<String, List<Integer>> entry : byMethod.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(method)) {
                    return entry.getValue();
                }
            }
        }

        // Methods other than GET and HEAD are not retried on a status code unless configured per method.
        if (!IDEMPOTENT_METHODS.contains(method.toUpperCase(Locale.ROOT))) {
            return List.of();
        }

        return runContext.render(configuration.getRetryOnStatusCodes()).asList(Integer.class);
    }

    /**
     * Whether this method can retry transport failures such as timeouts
     * or connection drops that occur after data has been sent.
     */
    private boolean isMethodEligibleForTransportRetry(String method) throws IllegalVariableEvaluationException {
        return runContext
            .render(configuration.getRetryableTransportFailureMethods())
            .asList(String.class)
            .stream()
            .anyMatch(configured -> configured.equalsIgnoreCase(method));
    }

    @SuppressWarnings("unchecked")
    private List<String> deniedList() {
        return (List<String>) ((DefaultRunContext) runContext).getTaskProperty("kestra.tasks.http.denied-list", List.class).orElse(Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    private void validateUri(URI uri) {
        List<String> allowedList = (List<String>) ((DefaultRunContext) runContext).getTaskProperty("kestra.tasks.http.allowed-list", List.class).orElse(Collections.emptyList());
        List<String> deniedList = deniedList();

        if (allowedList.isEmpty() && deniedList.isEmpty()) {
            return;
        }

        if (resolveAuthority(uri) == null) {
            // A list is configured but the URI carries no authority at all, so there is no host to check it
            // against: fail closed on both directions instead of silently letting an unmatchable URI through.
            throw new IllegalArgumentException("The URI %s has no resolvable host to check against the configured allow/deny lists (kestra.tasks.http.allowed-list / kestra.tasks.http.denied-list).".formatted(uri));
        }

        // first check that if there is an allow list, it matches one
        if (!allowedList.isEmpty()) {
            if (allowedList.stream().noneMatch(entry -> isListEntryMatch(entry, uri))) {
                throw new IllegalArgumentException("The URI %s is not in the configured allowed list (kestra.tasks.http.allowed-list).".formatted(uri));
            }
        }

        // then check that there are no exclusion for it
        if (deniedList.stream().anyMatch(entry -> isListEntryMatch(entry, uri))) {
            throw new IllegalArgumentException("The URI %s is in the configured denied list (kestra.tasks.http.denied-list).".formatted(uri));
        }

        if (this.hasProxy && !deniedList.isEmpty()) {
            // The proxy resolves the target, so the best Kestra can do is resolve it too; a name only the proxy can resolve is let through.
            String host = resolveAuthority(uri).host();
            if (parseIpLiteral(host).isEmpty()) {
                try {
                    rejectDeniedAddresses(host, SystemDefaultDnsResolver.INSTANCE.resolve(host), deniedList);
                } catch (UnknownHostException e) {
                    log.debug("Cannot resolve the host '{}' locally to check it against the denied list, leaving it to the proxy.", host);
                }
            }
        }
    }

    /**
     * The host and raw port ({@code -1} when none was given, same convention as {@link URI#getPort()}) Kestra is
     * actually about to connect to, as resolved by {@link #resolveAuthority(URI)}.
     */
    private record ResolvedAuthority(String host, int port) {
    }

    /**
     * Resolves the host and port of the URI Kestra is actually about to connect to, falling back to a manual
     * parse of the raw authority when {@link URI#getHost()} returns {@code null}. That happens for an authority
     * containing an underscore, or a partial dotted-decimal IPv4 literal such as {@code 169.254.43518}: strings
     * {@code java.net.URI}'s hostname grammar rejects, but that Apache HttpClient still connects to verbatim via
     * {@code URIUtils.extractHost}. Returns {@code null} only when the URI carries no authority at all.
     */
    private static ResolvedAuthority resolveAuthority(URI uri) {
        if (uri.getHost() != null) {
            return new ResolvedAuthority(uri.getHost(), uri.getPort());
        }

        String authority = uri.getAuthority();
        if (authority == null) {
            return null;
        }

        String hostAndPort = authority.substring(authority.lastIndexOf('@') + 1);
        int colon = hostAndPort.lastIndexOf(':');
        if (colon == -1) {
            return new ResolvedAuthority(hostAndPort, -1);
        }

        try {
            return new ResolvedAuthority(hostAndPort.substring(0, colon), Integer.parseInt(hostAndPort.substring(colon + 1)));
        } catch (NumberFormatException e) {
            return new ResolvedAuthority(hostAndPort, -1);
        }
    }

    /**
     * Parses {@code host} as an IP literal, stripping IPv6 brackets and a trailing dot first. Never resolves a
     * hostname: {@link InetAddress#ofLiteral} parses syntax only and never performs a DNS lookup, so this is safe
     * to call on every allow/deny comparison.
     */
    private static Optional<InetAddress> parseIpLiteral(String host) {
        String candidate = stripTrailingDot(host);
        if (candidate.startsWith("[") && candidate.endsWith("]")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }

        // ofLiteral rejects an interface-name zone id such as "%lo", yet the JDK resolver still connects to it.
        int zoneIndex = candidate.indexOf('%');
        if (zoneIndex != -1 && candidate.indexOf(':') != -1) {
            candidate = candidate.substring(0, zoneIndex);
        }

        try {
            return Optional.of(InetAddress.ofLiteral(candidate));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns the canonical dotted-quad / compressed-IPv6 form when {@code host} is any textual IP literal —
     * decimal, partial dotted-decimal, hex, or IPv4-mapped IPv6 — so that every spelling of the same address
     * matches the same allow/deny entry. Falls back to a lowercased, trailing-dot-stripped hostname otherwise.
     */
    private static String canonicalHost(String host) {
        return parseIpLiteral(host)
            .map(InetAddress::getHostAddress)
            .orElseGet(() -> stripTrailingDot(host).toLowerCase(Locale.ROOT));
    }

    /**
     * A {@code network/prefixLength} allow/deny-list entry, e.g. {@code 169.254.0.0/16}, so an entire range can
     * be denied without enumerating every textual spelling of every address in it.
     */
    private record Cidr(InetAddress network, int prefixLength, String scheme) {
        static Cidr parsed(String entry) {
            Matcher matcher = CIDR_ENTRY.matcher(entry);
            if (!matcher.matches()) {
                return null;
            }

            Optional<InetAddress> network = parseIpLiteral(matcher.group(2));
            if (network.isEmpty()) {
                return null;
            }

            int prefixLength;
            try {
                prefixLength = Integer.parseInt(matcher.group(3));
            } catch (NumberFormatException e) {
                return null;
            }

            if (prefixLength > network.get().getAddress().length * 8) {
                return null;
            }

            return new Cidr(network.get(), prefixLength, matcher.group(1));
        }

        boolean contains(String host) {
            return parseIpLiteral(host).map(this::contains).orElse(false);
        }

        boolean contains(InetAddress address) {
            byte[] addressBytes = address.getAddress();
            byte[] networkBytes = network.getAddress();
            if (addressBytes.length != networkBytes.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (addressBytes[i] != networkBytes[i]) {
                    return false;
                }
            }

            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }

            int mask = 0xFF << (8 - remainingBits);
            return (addressBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
        }
    }

    /**
     * Matches a {@code kestra.tasks.http.allowed-list} / {@code denied-list} entry against the URI Kestra is
     * actually about to connect to. Matching is done on the resolved authority (host, and scheme/port when the
     * entry specifies them) rather than on the raw URI string, so that URL-encoded userinfo
     * (e.g. {@code https://api.trusted.com@169.254.169.254/}, whose host is {@code 169.254.169.254}) or a
     * subdomain suffix (e.g. {@code https://api.trusted.com.attacker.example/}) cannot impersonate an entry
     * they merely start with. An IP-literal host is compared on its canonical form, so {@code 2852039166} and
     * {@code 169.254.169.254} match the same entry.
     * <p>
     * An entry matches its host exactly. Write it as {@code *.api.trusted.com} to also match every subdomain of
     * {@code api.trusted.com} (not {@code api.trusted.com} itself, which needs its own entry), or as a
     * {@code network/prefixLength} CIDR range (e.g. {@code 169.254.0.0/16}) to match every address in it.
     */
    private static boolean isListEntryMatch(String entry, URI uri) {
        ResolvedAuthority requestAuthority = resolveAuthority(uri);
        if (requestAuthority == null) {
            return false;
        }

        Cidr cidr = Cidr.parsed(entry);
        if (cidr != null) {
            return cidr.contains(requestAuthority.host())
                && (cidr.scheme() == null || cidr.scheme().equalsIgnoreCase(uri.getScheme()));
        }

        boolean matchSubdomains = entry.startsWith("*.") || entry.contains("://*.");
        String strippedEntry = entry.replace("://*.", "://").replaceFirst("^\\*\\.", "");

        URI entryUri;
        try {
            entryUri = URI.create(strippedEntry.contains("://") ? strippedEntry : "//" + strippedEntry);
        } catch (IllegalArgumentException e) {
            return false;
        }

        ResolvedAuthority entryAuthority = resolveAuthority(entryUri);
        if (entryAuthority == null) {
            return false;
        }

        String host = canonicalHost(requestAuthority.host());
        String lowerEntryHost = canonicalHost(entryAuthority.host());
        boolean hostMatches = matchSubdomains && parseIpLiteral(host).isEmpty()
            ? host.endsWith("." + lowerEntryHost)
            : host.equals(lowerEntryHost);
        if (!hostMatches) {
            return false;
        }

        if (entryUri.getScheme() != null && !entryUri.getScheme().equalsIgnoreCase(uri.getScheme())) {
            return false;
        }

        if (entryAuthority.port() != -1 && entryAuthority.port() != defaultedPort(uri.getScheme(), requestAuthority.port())) {
            return false;
        }

        String entryPath = entryUri.getPath();
        if (entryPath != null && !entryPath.isEmpty() && !"/".equals(entryPath)) {
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (!path.startsWith(entryPath)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Only CIDR and IP literal entries can match an address, and their scheme, port and path are ignored since a name is resolved without them.
     */
    private static boolean isDeniedAddress(String entry, InetAddress address) {
        Cidr cidr = Cidr.parsed(entry);
        if (cidr != null) {
            return cidr.contains(address);
        }

        String strippedEntry = entry.replace("://*.", "://").replaceFirst("^\\*\\.", "");
        try {
            ResolvedAuthority entryAuthority = resolveAuthority(URI.create(strippedEntry.contains("://") ? strippedEntry : "//" + strippedEntry));
            return entryAuthority != null && parseIpLiteral(entryAuthority.host()).map(address::equals).orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void rejectDeniedAddresses(String host, InetAddress[] addresses, List<String> deniedList) {
        for (InetAddress address : addresses) {
            if (deniedList.stream().anyMatch(entry -> isDeniedAddress(entry, address))) {
                throw new IllegalArgumentException("The host '%s' resolves to the address '%s', which is in the configured denied list (kestra.tasks.http.denied-list).".formatted(host, address.getHostAddress()));
            }
        }
    }

    /**
     * The addresses returned are the ones connected to, so a name cannot resolve to something else after the check.
     * IP literals are left to {@link #validateUri(URI)}, which also honors the scheme, port and path of an entry.
     */
    private final class DeniedAddressDnsResolver implements DnsResolver {
        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = SystemDefaultDnsResolver.INSTANCE.resolve(host);
            if (parseIpLiteral(host).isPresent()) {
                return addresses;
            }

            rejectDeniedAddresses(host, addresses, deniedList());
            return addresses;
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
        }
    }

    /**
     * Strips a single trailing dot from a hostname, e.g. {@code dangerous-url.com.}, since DNS resolves it
     * identically to {@code dangerous-url.com} and it would otherwise fail both the exact and suffix match.
     */
    private static String stripTrailingDot(String host) {
        return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    }

    /**
     * Returns {@code port}, or the scheme's default port when {@code port} is {@code -1}, so that an
     * allow/deny-list entry with an explicit default port still matches a request that omits it.
     */
    private static int defaultedPort(String scheme, int port) {
        if (port != -1) {
            return port;
        }

        return switch (scheme == null ? "" : scheme.toLowerCase(Locale.ROOT)) {
            case "https" -> 443;
            case "http" -> 80;
            default -> -1;
        };
    }

    @SuppressWarnings("unchecked")
    private <T> T bodyHandler(Class<?> cls, HttpEntity entity) throws IOException, ParseException {
        if (entity == null) {
            return null;
        } else if (String.class.isAssignableFrom(cls)) {
            return (T) EntityUtils.toString(entity);
        } else if (Byte[].class.isAssignableFrom(cls)) {
            return (T) ArrayUtils.toObject(EntityUtils.toByteArray(entity));
        } else if (MediaType.APPLICATION_YAML.equals(entity.getContentType()) || KestraMediaTypes.APPLICATION_X_YAML.equals(entity.getContentType())) {
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
