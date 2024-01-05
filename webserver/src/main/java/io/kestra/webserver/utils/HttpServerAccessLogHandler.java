package io.kestra.webserver.utils;

import com.google.common.net.HttpHeaders;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.StringUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jakarta.inject.Singleton;

// log user/identify + filter pour UI
/**
 * @author croudet
 */
@Sharable
@Singleton
@Requires(property = "kestra.server.access-log.enabled", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
public class HttpServerAccessLogHandler extends ChannelDuplexHandler {
    private static final AttributeKey<AccessLog> LOG_HANDLER_CONTEXT = AttributeKey.valueOf("logHandlerContext");
    private static final String MISSING = "-";

    private List<String> filters = new ArrayList<>();
    private String logFormat;
    private Logger accessLogger;

    public HttpServerAccessLogHandler(
        @Value("${kestra.server.access-log.name:access-log}") String name,
        @Value("${kestra.server.access-log.format}") String logFormat,
        @Value("${kestra.server.access-log.filters}") List<String> filters
    ) {
        this(LoggerFactory.getLogger(name));
        this.logFormat = logFormat;
        this.filters = filters;
    }

    private HttpServerAccessLogHandler(Logger accessLogger) {
        super();
        this.accessLogger = accessLogger;
    }

    private static String inetAddress(SocketChannel channel, HttpRequest request) {
        // maybe this request was proxied or load balanced. Try and get the real originating IP
        final String proxyChain = request.headers().get(HttpHeaders.X_FORWARDED_FOR, null);

        if (proxyChain != null) {
            // can contain multiple IPs for proxy chains. the first ip is our
            // client.
            final int firstComma = proxyChain.indexOf(',');
            if (firstComma != -1) {
                return proxyChain.substring(0, firstComma);
            } else {
                return proxyChain;
            }
        } else {
            return channel.remoteAddress().getHostString();
        }
    }

    private AccessLog accessLog(SocketChannel channel) {
        final Attribute<AccessLog> attr = channel.attr(LOG_HANDLER_CONTEXT);

        AccessLog accessLog = attr.get();
        if (accessLog == null) {
            accessLog = new AccessLog(logFormat);
            attr.set(accessLog);
        } else {
            accessLog.reset();
        }

        return accessLog;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (accessLogger.isInfoEnabled() && msg instanceof HttpRequest) {
            final SocketChannel channel = (SocketChannel) ctx.channel();
            AccessLog accessLog = accessLog(channel);
            final HttpRequest request = (HttpRequest) msg;
            accessLog.startTime = System.nanoTime();
            accessLog.inetAddress = inetAddress(channel, request);
            accessLog.port = channel.localAddress().getPort();
            accessLog.method = request.method().name();
            accessLog.uri = request.uri();
            accessLog.protocol = request.protocolVersion().text();
        }

        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // modify message on way out to add headers if needed
        if (accessLogger.isInfoEnabled()) {
            processWriteEvent(ctx, msg, promise);
        } else {
            super.write(ctx, msg, promise);
        }
    }

    private void logAtLast(ChannelHandlerContext ctx, Object msg, ChannelPromise promise, AccessLog accessLog) {
        //FIXME cannot attach a listener to a void future
//        ctx.write(msg, promise).addListener(future -> {
//            if (future.isSuccess()) {
//                accessLog.logAccess(accessLogger, filters);
//            }
//        });

        ctx.write(msg, promise);
    }

    private void processWriteEvent(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        final AccessLog accessLog = ctx.channel().attr(LOG_HANDLER_CONTEXT).get();
        if (accessLog != null && accessLog.method != null) {
            if (msg instanceof HttpResponse) {
                final HttpResponse response = (HttpResponse) msg;
                final HttpResponseStatus status = response.status();

                if (status.equals(HttpResponseStatus.CONTINUE)) {
                    ctx.write(msg, promise);
                    return;
                }
                final boolean chunked = HttpUtil.isTransferEncodingChunked(response);
                accessLog.chunked = chunked;
                accessLog.status = status.code();
                if (!chunked) {
                    accessLog.contentLength = HttpUtil.getContentLength(response, -1L);
                }
            }

            if (msg instanceof LastHttpContent) {
                accessLog.increaseContentLength(((LastHttpContent) msg).content().readableBytes());
                logAtLast(ctx, msg, promise, accessLog);
                return;
            } else if (msg instanceof ByteBuf) {
                accessLog.increaseContentLength(((ByteBuf) msg).readableBytes());
            } else if (msg instanceof ByteBufHolder) {
                accessLog.increaseContentLength(((ByteBufHolder) msg).content().readableBytes());
            }
        }

        super.write(ctx, msg, promise);
    }

    private static class AccessLog {
        private String logFormat;
        private String inetAddress;
        private String method;
        private String uri;
        private String protocol;
        private int port;
        private boolean chunked;
        private int status;
        private long startTime;
        private long contentLength;
        private String zonedDateTime;

        AccessLog(String logFormat) {
            this.logFormat = logFormat;
            this.zonedDateTime = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }

        private void reset() {
            this.zonedDateTime = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            inetAddress = null;
            method = null;
            uri = null;
            protocol = null;
            port = -1;
            status = -1;
            startTime = 0L;
            contentLength = 0L;
            chunked = false;
        }

        void increaseContentLength(long contentLength) {
            if (chunked) {
                this.contentLength += contentLength;
            }
        }

        void logAccess(Logger accessLogger, List<String> filters) {
            if (accessLogger.isInfoEnabled()) {
                final long timeElapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
                String message = MessageFormatter.arrayFormat(logFormat, new Object[]{
                    zonedDateTime,
                    timeElapsed,
                    method,
                    uri,
                    status,
                    contentLength > -1L ? contentLength : MISSING,
                    inetAddress,
                    port
                }).getMessage();

                boolean filtered = filters
                    .stream()
                    .anyMatch(message::matches);

                if (!filtered && status < 500) {
                    return;
                }

                if (status >= 400) {
                    accessLogger.warn(message);
                } else {
                    accessLogger.info(message);
                }
            }
        }
    }
}
