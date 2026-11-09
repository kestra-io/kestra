
package io.kestra.plugin.core.http;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TestsUtils;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@io.micronaut.context.annotation.Property(name = "kestra.tasks.http.denied-list[0]", value = "dangerous-url.com")

@KestraTest
class WebsocketRequestTest {

    @Inject
    private TestRunContextFactory runContextFactory;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private Channel serverChannel;
    private String serverUri;
    private volatile String receivedAuthorization;

    @BeforeEach
    void startServer() throws InterruptedException {
        receivedAuthorization = null;
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();

        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) {
                    channel.pipeline()
                        .addLast(new HttpServerCodec())
                        .addLast(new HttpObjectAggregator(65536))
                        .addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                            @Override
                            protected void channelRead0(
                                ChannelHandlerContext ctx,
                                FullHttpRequest request) {
                                receivedAuthorization = request
                                    .headers()
                                    .get("Authorization");
                                ctx.fireChannelRead(request.retain());
                            }
                        })

                        .addLast(new WebSocketServerProtocolHandler("/ws"))
                        .addLast(new SimpleChannelInboundHandler<WebSocketFrame>() {
                            @Override
                            protected void channelRead0(
                                ChannelHandlerContext ctx,
                                WebSocketFrame frame) {

                                if (frame instanceof TextWebSocketFrame textFrame) {
                                    if (
                                        "BINARY".equals(
                                            textFrame
                                                .text()
                                        )
                                    ) {
                                        ctx.writeAndFlush(
                                            new BinaryWebSocketFrame(
                                                ctx.alloc().buffer(
                                                    3
                                                )
                                                    .writeBytes(
                                                        new byte[] { 1, 2,
                                                            3 }
                                                    )
                                            )
                                        );
                                    } else if (
                                        "CLOSE".equals(
                                            textFrame.text()
                                        )
                                    ) {
                                        ctx.writeAndFlush(
                                            new io.netty.handler.codec.http.websocketx.CloseWebSocketFrame(
                                                1000,
                                                "Normal closure"
                                            )
                                        );
                                    } else if (
                                        "JSON".equals(
                                            textFrame.text()
                                        )
                                    ) {
                                        ctx.writeAndFlush(
                                            new TextWebSocketFrame(
                                                "{\"message\":\"Hello Kestra\"}"
                                            )
                                        );
                                    } else {
                                        ctx.writeAndFlush(
                                            new TextWebSocketFrame(
                                                "Echo: " + textFrame
                                                    .text()
                                            )
                                        );
                                    }
                                }
                            }
                        });
                }
            });

        serverChannel = bootstrap
            .bind(new InetSocketAddress("127.0.0.1", 0))
            .sync()
            .channel();

        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();
        serverUri = "ws://127.0.0.1:" + port + "/ws";
    }

    @AfterEach
    void stopServer() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    void shouldSendAndReceiveMessage() throws Exception {
        WebsocketRequest task = WebsocketRequest.builder()
            .id("websocket-test")
            .type("io.kestra.plugin.core.http.WebsocketRequest")
            .uri(Property.ofValue(serverUri))
            .messages(Property.ofValue(List.of("Hello Kestra")))
            .maxMessages(Property.ofValue(1))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(
            runContextFactory,
            task,
            Map.of()
        );

        WebsocketRequest.Output output = task.run(runContext);

        assertEquals(1, output.getMessages().size());
        assertEquals("Echo: Hello Kestra", output.getMessages().get(0));
    }

    @Test
    void shouldSendAndReceiveMultipleMessages() throws Exception {
        WebsocketRequest task = WebsocketRequest.builder()
            .id("websocket-multiple-test")
            .type("io.kestra.plugin.core.http.WebsocketRequest")
            .uri(Property.ofValue(serverUri))
            .messages(
                Property.ofValue(
                    List.of(
                        "First message",
                        "Second message",
                        "Third message",
                        "Fourth message",
                        "Fifth message"
                    )
                )
            )
            .maxMessages(Property.ofValue(3))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(
            runContextFactory,
            task,
            Map.of()
        );

        WebsocketRequest.Output output = task.run(runContext);

        assertEquals(3, output.getMessages().size());
        assertEquals("Echo: First message", output.getMessages().get(0));
        assertEquals("Echo: Second message", output.getMessages().get(1));
        assertEquals("Echo: Third message", output.getMessages().get(2));
    }

    @Test
    void shouldStopAfterMaxDuration() throws Exception {
        WebsocketRequest task = WebsocketRequest.builder()
            .id("websocket-timeout-test")
            .type("io.kestra.plugin.core.http.WebsocketRequest")
            .uri(Property.ofValue(serverUri))
            .maxDuration(Property.ofValue(java.time.Duration.ofMillis(500)))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(
            runContextFactory,
            task,
            Map.of()
        );

        long start = System.currentTimeMillis();

        WebsocketRequest.Output output = task.run(runContext);

        long elapsed = System.currentTimeMillis() - start;

        assertEquals(0, output.getMessages().size());
        assertEquals(true, elapsed >= 500);
    }

    @Test
    void shouldExtractValuesUsingJq() throws Exception {
        WebsocketRequest task = WebsocketRequest.builder()
            .id("websocket-jq-test")
            .type("io.kestra.plugin.core.http.WebsocketRequest")
            .uri(Property.ofValue(serverUri))
            .messages(Property.ofValue(List.of("JSON")))
            .maxMessages(Property.ofValue(1))
            .concatJqExpression(Property.ofValue(".message"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(
            runContextFactory,
            task,
            Map.of()
        );

        WebsocketRequest.Output output = task.run(runContext);

        assertEquals(1, output.getMessages().size());
        assertEquals("{\"message\":\"Hello Kestra\"}", output.getMessages().get(0));
        assertEquals("Hello Kestra", output.getResult());
    }

    @Test
    void shouldStopWhenConditionMatches() throws Exception {
        WebsocketRequest task = WebsocketRequest.builder()
            .id("websocket-stop-condition-test")
            .type("io.kestra.plugin.core.http.WebsocketRequest")
            .uri(Property.ofValue(serverUri))
            .messages(Property.ofValue(List.of("JSON")))
            .stopCondition(Property.ofValue(".message == \"Hello Kestra\""))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(
            runContextFactory,
            task,
            Map.of()
        );

        WebsocketRequest.Output output = task.run(runContext);

        assertEquals(1, output.getMessages().size());
        assertEquals("{\"message\":\"Hello Kestra\"}", output.getMessages().get(0));
    }

    @Test
    void shouldHandleServerClose() throws Exception {
        WebsocketRequest task = WebsocketRequest.builder()
            .id("websocket-server-close-test")
            .type("io.kestra.plugin.core.http.WebsocketRequest")
            .uri(Property.ofValue(serverUri))
            .messages(Property.ofValue(List.of("CLOSE")))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(
            runContextFactory,
            task,
            Map.of()
        );

        WebsocketRequest.Output output = task.run(runContext);

        assertEquals(0, output.getMessages().size());
        assertEquals(1000, output.getCloseCode());
        assertEquals("Normal closure", output.getCloseReason());
    }

    @Test
    void shouldSendAuthenticationHeader() throws Exception {
        WebsocketRequest task = WebsocketRequest.builder()
            .id("websocket-auth-test")
            .type("io.kestra.plugin.core.http.WebsocketRequest")
            .uri(Property.ofValue(serverUri))
            .headers(
                Property.ofValue(
                    Map.of(
                        "Authorization", "Bearer test-token"
                    )
                )
            )
            .messages(Property.ofValue(List.of("Hello Auth")))
            .maxMessages(Property.ofValue(1))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(
            runContextFactory,
            task,
            Map.of()
        );
        WebsocketRequest.Output output = task.run(runContext);

        assertEquals("Echo: Hello Auth", output.getMessages().get(0));
        assertEquals("Bearer test-token", receivedAuthorization);
    }

    @Test
    void shouldReceiveBinaryMessageAsBase64() throws Exception {
        WebsocketRequest task = WebsocketRequest.builder()
            .id("websocket-binary-test")
            .type("io.kestra.plugin.core.http.WebsocketRequest")
            .uri(Property.ofValue(serverUri))
            .messages(Property.ofValue(List.of("BINARY")))
            .maxMessages(Property.ofValue(1))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(
            runContextFactory,
            task,
            Map.of()
        );

        WebsocketRequest.Output output = task.run(runContext);

        assertEquals(1, output.getMessages().size());
        assertEquals("AQID", output.getMessages().get(0));
    }

    @Test
    void shouldRejectOversizedTextMessage() throws Exception {
        WebsocketRequest task = WebsocketRequest.builder()
            .id("websocket-large-text-test")
            .type("io.kestra.plugin.core.http.WebsocketRequest")
            .uri(Property.ofValue(serverUri))
            .messages(Property.ofValue(List.of("A".repeat(1024))))
            .maxMessages(Property.ofValue(1))
            .maxBufferSize(Property.ofValue(100L))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(
            runContextFactory,
            task,
            Map.of()
        );

        assertThrows(
            Exception.class,
            () -> task.run(runContext)
        );
    }

    @Test
    void shouldRejectOversizedBinaryMessage() throws Exception {
        WebsocketRequest task = WebsocketRequest.builder()
            .id("websocket-large-binary-test")
            .type("io.kestra.plugin.core.http.WebsocketRequest")
            .uri(Property.ofValue(serverUri))
            .messages(Property.ofValue(List.of("BINARY")))
            .maxMessages(Property.ofValue(1))
            .maxBufferSize(Property.ofValue(2L))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(
            runContextFactory,
            task,
            Map.of()
        );

        assertThrows(
            Exception.class,
            () -> task.run(runContext)
        );
    }

    @Test
    void shouldSpillMessagesToStorageWhenBufferLimitIsExceeded() throws Exception {
        WebsocketRequest task = WebsocketRequest.builder()
            .id("websocket-storage-test")
            .type("io.kestra.plugin.core.http.WebsocketRequest")
            .uri(Property.ofValue(serverUri))
            .messages(Property.ofValue(List.of("First", "Second")))
            .maxMessages(Property.ofValue(2))
            .maxBufferSize(Property.ofValue(20L))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(
            runContextFactory,
            task,
            Map.of()
        );

        WebsocketRequest.Output output = task.run(runContext);

        assertNotNull(output.getMessagesUri());
        assertEquals(0, output.getMessages().size());
        assertEquals(2, output.getSize());

        try (var inputStream = runContext.storage().getFile(output.getMessagesUri())) {
            String storedContent = new String(
                inputStream.readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8
            );

            assertEquals(
                List.of("\"Echo: First\"", "\"Echo: Second\""),
                storedContent.lines().toList()
            );
        }
    }

    @Test
    void shouldRejectDeniedWebSocketHost() throws Exception {
        WebsocketRequest task = WebsocketRequest.builder()
            .id("websocket-ssrf-test")
            .type("io.kestra.plugin.core.http.WebsocketRequest")
            .uri(Property.ofValue("ws://dangerous-url.com/ws"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(
            runContextFactory,
            task,
            Map.of(
                "kestra.tasks.http.denied-list",
                List.of("dangerous-url.com")
            )
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> task.run(runContext)
        );

        assertEquals(
            "The URI ws://dangerous-url.com/ws is in the configured denied list (kestra.tasks.http.denied-list).",
            exception.getMessage()
        );
    }
}