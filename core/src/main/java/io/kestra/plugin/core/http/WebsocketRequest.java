
package io.kestra.plugin.core.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.kestra.core.http.client.HttpUriValidator;
import io.kestra.core.http.client.configurations.AbstractAuthConfiguration;
import io.kestra.core.http.client.configurations.BasicAuthConfiguration;
import io.kestra.core.http.client.configurations.BearerAuthConfiguration;
import io.kestra.core.http.client.configurations.ProxyConfiguration;
import io.kestra.core.http.client.configurations.TimeoutConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;


@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(title = "Consume messages from a WebSocket endpoint.")
@Plugin(examples = {
        @Example(title = "Connect to a WebSocket endpoint and receive messages.", full = true, code = """
                id: websocket_consumer
                namespace: company.team

                tasks:
                  - id: consume_messages
                    type: io.kestra.plugin.core.http.WebsocketRequest
                    uri: ws://localhost:8080/ws
                    maxMessages: 10
                    maxDuration: PT1M
                """),
        @Example(title = "Send messages and receive responses from a WebSocket endpoint.", full = true, code = """
                id: websocket_echo
                namespace: company.team

                tasks:
                  - id: websocket_request
                    type: io.kestra.plugin.core.http.WebsocketRequest
                    uri: wss://example.com/ws
                    messages:
                      - "Hello"
                      - "How are you?"
                    maxMessages: 2
                """),
        @Example(title = "Connect to an authenticated WebSocket endpoint.", full = true, code = """
                id: websocket_authenticated
                namespace: company.team

                tasks:
                  - id: authenticated_websocket
                    type: io.kestra.plugin.core.http.WebsocketRequest
                    uri: wss://api.example.com/ws
                    headers:
                      Authorization: "Bearer {{ secret('API_TOKEN') }}"
                    maxMessages: 5
                """)
}, metrics = {
        @Metric(name = "size", type = "counter", description = "The number of messages received from the WebSocket endpoint")
})
public class WebsocketRequest extends AbstractHttp implements RunnableTask<WebsocketRequest.Output> {

    private static final Scope SCOPE;

    static {
        SCOPE = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, SCOPE);
    }

    @Schema(title = "Messages to send after establishing the WebSocket connection.")
    private Property<List<String>> messages;

    @Schema(title = "Maximum number of messages to receive.")
    private Property<Integer> maxMessages;

    @Schema(title = "Maximum duration for collecting messages.")
    private Property<Duration> maxDuration;

    @Builder.Default
    @Schema(title = "Maximum size in bytes to keep received messages in memory before using internal storage.")
    private Property<Long> maxBufferSize = Property.ofValue(10 * 1024 * 1024L);

    @Builder.Default
    @Schema(title = "Maximum size in bytes allowed for an individual received message.")
    private Property<Long> maxMessageSize = Property.ofValue(10 * 1024 * 1024L);

    @Schema(title = "Condition that stops collecting text messages when it evaluates to true. This option applies only to text messages; binary messages do not evaluate this condition.")
    private Property<String> stopCondition;

    @Schema(title = "JQ expression used to extract and concatenate values from received text messages. This option applies only to text messages; binary messages are stored as Base64.")
    private Property<String> concatJqExpression;

    @Builder.Default
    @Schema(title = "Whether missing JQ results should fail the task.")
    private Property<Boolean> failedOnMissingJq = Property.ofValue(true);

    @Override
    public Output run(RunContext runContext) throws Exception {
        io.kestra.core.http.HttpRequest request = this.request(runContext);
        URI uri = request.getUri();

        String scheme = uri.getScheme();
        if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("WebSocket URI must use ws:// or wss://");
        }

        if (runContext instanceof DefaultRunContext defaultRunContext) {
            HttpUriValidator.validate(
                    uri,
                    defaultRunContext,
                    this.options != null && this.options.getProxy() != null);
        }

        int messageLimit = this.maxMessages == null
                ? Integer.MAX_VALUE
                : runContext.render(this.maxMessages)
                        .as(Integer.class)
                        .orElse(Integer.MAX_VALUE);

        if (messageLimit <= 0) {
            throw new IllegalArgumentException("maxMessages must be greater than zero");
        }

        Duration duration = this.maxDuration == null
                ? Duration.ofMinutes(5)
                : runContext.render(this.maxDuration)
                        .as(Duration.class)
                        .orElse(Duration.ofMinutes(5));

        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("maxDuration must be positive");
        }

        long maxBufferSize = this.maxBufferSize == null
                ? 10 * 1024 * 1024L
                : runContext.render(this.maxBufferSize)
                        .as(Long.class)
                        .orElse(10 * 1024 * 1024L);

        if (maxBufferSize <= 0) {
            throw new IllegalArgumentException("maxBufferSize must be greater than zero");
        }

        long maxMessageSize = this.maxMessageSize == null
                ? 10 * 1024 * 1024L
                : runContext.render(this.maxMessageSize)
                        .as(Long.class)
                        .orElse(10 * 1024 * 1024L);

        if (maxMessageSize <= 0) {
            throw new IllegalArgumentException("maxMessageSize must be greater than zero");
        }

        JsonQuery stopQuery = this.stopCondition == null
                ? null
                : JsonQuery.compile(
                        runContext.render(this.stopCondition)
                                .as(String.class)
                                .orElseThrow(() -> new IllegalArgumentException("stopCondition cannot be empty")),
                        Versions.JQ_1_6);

        JsonQuery concatQuery = this.concatJqExpression == null
                ? null
                : JsonQuery.compile(
                        runContext.render(this.concatJqExpression)
                                .as(String.class)
                                .orElseThrow(() -> new IllegalArgumentException("concatJqExpression cannot be empty")),
                        Versions.JQ_1_6);

        boolean failOnMissing = this.failedOnMissingJq == null
                || runContext.render(this.failedOnMissingJq)
                        .as(Boolean.class)
                        .orElse(true);

        WebsocketMessageBuffer messageBuffer = new WebsocketMessageBuffer(runContext, maxBufferSize);

        AtomicLong receivedMessageCount = new AtomicLong(0);
        StringBuffer jqResults = new StringBuffer();
        AtomicLong jqResultSize = new AtomicLong(0);
        AtomicLong jqResultCount = new AtomicLong(0);

        AtomicLong totalSize = new AtomicLong(0);
        AtomicLong lastMessageTime = new AtomicLong(System.nanoTime());
        AtomicReference<Integer> closeCode = new AtomicReference<>();
        AtomicReference<String> closeReason = new AtomicReference<>();
        AtomicReference<Throwable> processingError = new AtomicReference<>();

        CompletableFuture<Void> completed = new CompletableFuture<>();
        StringBuilder currentMessage = new StringBuilder();
        AtomicLong currentMessageSize = new AtomicLong(0);
        ByteArrayOutputStream currentBinaryMessage = new ByteArrayOutputStream();
        AtomicLong currentBinaryMessageSize = new AtomicLong(0);

        Duration connectTimeout = Duration.ofSeconds(30);

        if (this.options != null && this.options.getTimeout() != null) {
            TimeoutConfiguration timeout = this.options.getTimeout();

            if (timeout.getConnectTimeout() != null) {
                connectTimeout = runContext.render(timeout.getConnectTimeout())
                        .as(Duration.class)
                        .orElse(connectTimeout);
            }
        }

        java.net.http.HttpClient.Builder clientBuilder = java.net.http.HttpClient.newBuilder()
                .connectTimeout(connectTimeout);

        boolean proxyAuthenticationConfigured = false;

        if (this.options != null && this.options.getProxy() != null) {
            ProxyConfiguration proxy = this.options.getProxy();

            if (proxy.getType() != null) {
                java.net.Proxy.Type proxyType = runContext.render(proxy.getType())
                        .as(java.net.Proxy.Type.class)
                        .orElse(java.net.Proxy.Type.DIRECT);

                if (proxyType == java.net.Proxy.Type.HTTP) {
                    String address = runContext.render(proxy.getAddress())
                            .as(String.class)
                            .orElseThrow(() -> new IllegalArgumentException("Proxy address is required"));

                    Integer port = runContext.render(proxy.getPort())
                            .as(Integer.class)
                            .orElseThrow(() -> new IllegalArgumentException("Proxy port is required"));

                    String username = runContext.render(proxy.getUsername())
                            .as(String.class)
                            .orElse(null);

                    String password = runContext.render(proxy.getPassword())
                            .as(String.class)
                            .orElse(null);

                    clientBuilder.proxy(ProxySelector.of(new InetSocketAddress(address, port)));

                    if (username != null && password != null) {
                        proxyAuthenticationConfigured = true;

                        clientBuilder.authenticator(new java.net.Authenticator() {
                            @Override
                            protected java.net.PasswordAuthentication getPasswordAuthentication() {
                                if (getRequestorType() == RequestorType.PROXY) {
                                    return new java.net.PasswordAuthentication(
                                            username,
                                            password.toCharArray());
                                }
                                return null;
                            }
                        });
                    }
                } else if (proxyType != java.net.Proxy.Type.DIRECT) {
                    throw new IllegalArgumentException("Unsupported proxy type: " + proxyType);
                }
            }
        }

        if (this.options != null && this.options.getSsl() != null
                && this.options.getSsl().getInsecureTrustAllCertificates() != null
                && runContext.render(this.options.getSsl().getInsecureTrustAllCertificates())
                        .as(Boolean.class).orElse(false)) {

            TrustManager[] trustAllManagers = new TrustManager[] {
                    new X509TrustManager() {

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    } };

            SSLContext sslContext = SSLContext.getInstance(
                    "TLS");
            sslContext.init(null, trustAllManagers, new java.security.SecureRandom());
            clientBuilder.sslContext(sslContext);
        }

        java.net.http.HttpClient client = clientBuilder.build();

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                runContext.logger().info("WebSocket connection established");
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                long newSize = currentMessageSize.addAndGet(
                        data.toString().getBytes(StandardCharsets.UTF_8).length);

                if (newSize > maxMessageSize) {
                    IllegalStateException exception = new IllegalStateException(
                            "WebSocket text message exceeded the maximum allowed size of " + maxMessageSize + " bytes");
                    processingError.set(exception);
                    completed.completeExceptionally(exception);
                    return CompletableFuture.completedFuture(null);
                }

                currentMessage.append(data);

                if (last) {
                    String message = currentMessage.toString();
                    currentMessage.setLength(0);
                    currentMessageSize.set(0);

                    try {
                        messageBuffer.add(message);
                        receivedMessageCount.incrementAndGet();
                    } catch (IOException e) {
                        processingError.set(e);
                        completed.completeExceptionally(e);
                        return CompletableFuture.completedFuture(null);
                    }

                    lastMessageTime.set(System.nanoTime());
                    totalSize.addAndGet(message.getBytes(StandardCharsets.UTF_8).length);

                    try {
                        JsonNode input = null;

                        if (concatQuery != null || stopQuery != null) {
                            input = JacksonMapper.ofJson().readTree(message);
                        }

                        if (concatQuery != null) {
                            AtomicBoolean foundResult = new AtomicBoolean(false);

                            concatQuery.apply(Scope.newChildScope(SCOPE), input, value -> {
                                foundResult.set(true);

                                String resultValue;

                                if (value instanceof TextNode textNode) {
                                    resultValue = textNode.asText();
                                } else if (value instanceof NumericNode || value instanceof BooleanNode) {
                                    resultValue = value.toString();
                                } else {
                                    throw new IllegalArgumentException(
                                            "concatJqExpression must return string, number or boolean values");
                                }

                                long resultBytes = resultValue.getBytes(StandardCharsets.UTF_8).length;
                                long newResultSize = jqResultSize.addAndGet(resultBytes);

                                if (newResultSize > maxBufferSize) {
                                    jqResultSize.addAndGet(-resultBytes);
                                    throw new IllegalStateException(
                                            "Accumulated JQ results exceeded maxBufferSize of "
                                                    + maxBufferSize + " bytes");
                                }

                                jqResults.append(resultValue);
                                jqResultCount.incrementAndGet();
                            });

                            if (!foundResult.get() && failOnMissing) {
                                throw new IllegalArgumentException(
                                        "concatJqExpression returned no result for a received message");
                            }
                        }

                        boolean shouldStop = false;

                        if (stopQuery != null) {
                            AtomicBoolean matched = new AtomicBoolean(false);

                            stopQuery.apply(Scope.newChildScope(SCOPE), input, value -> {
                                if (!(value instanceof BooleanNode)) {
                                    throw new IllegalArgumentException("stopCondition must return a boolean value");
                                }

                                if (value.booleanValue()) {
                                    matched.set(true);
                                }
                            });

                            shouldStop = matched.get();
                        }

                        if (shouldStop || receivedMessageCount.get() >= messageLimit) {
                            completed.complete(null);
                        } else {
                            webSocket.request(1);
                        }
                    } catch (Exception e) {
                        processingError.set(e);
                        completed.completeExceptionally(e);
                        return CompletableFuture.completedFuture(null);
                    }
                } else {
                    webSocket.request(1);
                }

                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {

                ByteBuffer copy = data.asReadOnlyBuffer();
                byte[] bytes = new byte[copy.remaining()];
                copy.get(bytes);
                long newSize = currentBinaryMessageSize.addAndGet(bytes.length);

                if (newSize > maxMessageSize) {
                    IllegalStateException exception = new IllegalStateException(
                            "WebSocket binary message exceeded the maximum allowed size of " + maxMessageSize
                                    + " bytes");
                    processingError.set(exception);
                    completed.completeExceptionally(exception);
                    return CompletableFuture.completedFuture(null);
                }

                currentBinaryMessage.write(bytes, 0, bytes.length);

                if (last) {
                    byte[] binaryMessage = currentBinaryMessage.toByteArray();
                    currentBinaryMessage.reset();
                    currentBinaryMessageSize.set(0);

                    String encodedMessage = Base64.getEncoder().encodeToString(binaryMessage);

                    try {
                        messageBuffer.add(encodedMessage);
                        receivedMessageCount.incrementAndGet();
                    } catch (IOException e) {
                        processingError.set(e);
                        completed.completeExceptionally(e);
                        return CompletableFuture.completedFuture(null);
                    }

                    lastMessageTime.set(System.nanoTime());
                    totalSize.addAndGet(binaryMessage.length);

                    if (receivedMessageCount.get() >= messageLimit) {
                        completed.complete(null);
                    } else {
                        webSocket.request(1);
                    }
                } else {
                    webSocket.request(1);
                }

                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
                webSocket.request(1);
                return webSocket.sendPong(message);
            }

            @Override
            public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                closeCode.set(statusCode);
                closeReason.set(reason);
                completed.complete(null);

                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                completed.completeExceptionally(error);
            }
        };

        WebSocket.Builder builder = client.newWebSocketBuilder()
                .connectTimeout(connectTimeout);

        if (this.options != null && this.options.getAuth() != null) {
            AbstractAuthConfiguration auth = this.options.getAuth();

            if (auth instanceof BasicAuthConfiguration basicAuth) {
                String username = runContext.render(basicAuth.getUsername())
                        .as(String.class)
                        .orElseThrow(() -> new IllegalArgumentException("Basic authentication username is missing"));

                String password = runContext.render(basicAuth.getPassword())
                        .as(String.class)
                        .orElseThrow(() -> new IllegalArgumentException("Basic authentication password is missing"));

                String credentials = Base64.getEncoder().encodeToString(
                        (username + ":" + password).getBytes(StandardCharsets.UTF_8));

                builder.header("Authorization", "Basic " + credentials);

            } else if (auth instanceof BearerAuthConfiguration bearerAuth) {
                String token = runContext.render(bearerAuth.getToken())
                        .as(String.class)
                        .orElseThrow(() -> new IllegalArgumentException("Bearer authentication token is missing"));

                builder.header("Authorization", "Bearer " + token);

            } else {
                throw new IllegalArgumentException("Unsupported WebSocket authentication type: " + auth.getType());
            }
        }

        request.getHeaders().map().forEach((name, values) -> {
            if (!name.equalsIgnoreCase("Content-Type") && !name.equalsIgnoreCase("Content-Length")
                    && !name.equalsIgnoreCase("Host") && !name.equalsIgnoreCase("Connection")
                    && !name.equalsIgnoreCase("Upgrade")) {
                values.forEach(value -> builder.header(name, value));
            }
        });
        WebSocket socket = null;
        long deadline = System.nanoTime() + duration.toNanos();
        CompletableFuture<WebSocket> connection = builder.buildAsync(uri, listener);

        try {
            long connectionRemaining = deadline - System.nanoTime();

            if (connectionRemaining <= 0) {
                throw new TimeoutException("WebSocket connection exceeded maxDuration");
            }

            try {
                socket = connection.get(connectionRemaining, TimeUnit.NANOSECONDS);
            } catch (java.util.concurrent.ExecutionException e) {
                if (proxyAuthenticationConfigured && "wss".equalsIgnoreCase(scheme)) {
                    throw new IllegalStateException(
                            "WebSocket connection through the authenticated proxy failed. "
                                    + "If the proxy requires Basic authentication for HTTPS tunnelling, "
                                    + "the JDK may disable it by default. Configure the JVM with "
                                    + "-Djdk.http.auth.tunneling.disabledSchemes= to enable it.",
                            e);
                }

                throw e;
            }
            if (this.messages != null) {
                List<String> messagesToSend = runContext.render(this.messages)
                        .asList(String.class);

                for (String message : messagesToSend) {
                    long remaining = deadline - System.nanoTime();

                    if (remaining <= 0) {
                        throw new TimeoutException("WebSocket message sending exceeded maxDuration");
                    }

                    socket.sendText(message, true).get(remaining, TimeUnit.NANOSECONDS);
                }
            }
            Duration readIdleTimeout = Duration.ofMinutes(5);

            if (this.options != null && this.options.getTimeout() != null
                    && this.options.getTimeout().getReadIdleTimeout() != null) {
                readIdleTimeout = runContext.render(
                        this.options.getTimeout().getReadIdleTimeout()).as(Duration.class).orElse(readIdleTimeout);
            }

            while (!completed.isDone()) {
                long elapsedSinceLastMessage = System.nanoTime() - lastMessageTime.get();

                long idleRemaining = readIdleTimeout.toNanos() - elapsedSinceLastMessage;
                long durationRemaining = deadline - System.nanoTime();

                if (idleRemaining <= 0 || durationRemaining <= 0) {
                    runContext.logger().info(
                            "WebSocket collection stopped because its timeout was reached");
                    break;
                }

                try {
                    completed.get(
                            Math.min(idleRemaining, durationRemaining),
                            TimeUnit.NANOSECONDS);
                } catch (TimeoutException e) {
                    // continue waiting
                }
            }

            if (processingError.get() != null) {
                throw new IllegalStateException(
                        "Failed to process a WebSocket message",
                        processingError.get());
            }

            if (concatQuery != null && jqResultCount.get() == 0 && failOnMissing) {
                throw new IllegalArgumentException(
                        "concatJqExpression did not produce any results");
            }

            if (!socket.isOutputClosed() && !socket.isInputClosed()) {
                try {
                    socket.sendClose(
                            WebSocket.NORMAL_CLOSURE,
                            "Message collection completed").get(5, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException e) {
                    if (socket.isOutputClosed() || socket.isInputClosed()) {
                        runContext.logger().debug(
                                "WebSocket connection was already closed by the remote endpoint");
                    } else {
                        throw e;
                    }
                }
            }
            String result = concatQuery == null
                    ? null
                    : jqResults.toString();

            WebsocketMessageBuffer.URIResult bufferedMessages = messageBuffer.finish();

            runContext.metric(Counter.of("size", receivedMessageCount.get()));

            return Output.builder().uri(uri).messages(bufferedMessages.getMessages())
                    .messagesUri(bufferedMessages.getMessagesUri()).size(receivedMessageCount.get())
                    .closeCode(closeCode.get()).closeReason(closeReason.get()).result(result).build();
        } catch (InterruptedException e) {
            connection.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        } catch (TimeoutException e) {
            connection.cancel(true);
            throw e;
        } finally {
            if (socket != null && !socket.isOutputClosed()) {
                socket.abort();
            }

            try {
                messageBuffer.close();
            } catch (IOException e) {
                runContext.logger().error("Failed to close WebSocket message buffer", e);
            }
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(title = "The WebSocket endpoint URI.")
        private final URI uri;

        @Schema(title = "Messages received from the WebSocket endpoint.")
        private final List<String> messages;

        @Schema(title = "URI of the file containing received messages when the buffer limit is exceeded.")
        private final URI messagesUri;

        @Schema(title = "The number of messages received.")
        private final long size;

        @Schema(title = "WebSocket close status code.")
        private final Integer closeCode;

        @Schema(title = "WebSocket close reason.")
        private final String closeReason;

        @Schema(title = "Concatenated JQ result.")
        private final String result;
    }
}
