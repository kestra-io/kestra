
package io.kestra.plugin.core.http;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import lombok.Getter;

public class WebsocketMessageBuffer implements AutoCloseable {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private final RunContext runContext;
    private final long maxBufferSize;
    private final List<String> messages = new ArrayList<>();

    private long bufferedSize;
    private Path file;
    private BufferedWriter writer;
    private long messageCount;

    public WebsocketMessageBuffer(RunContext runContext, long maxBufferSize) {
        this.runContext = runContext;
        this.maxBufferSize = maxBufferSize;
    }

    public synchronized void add(String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);

        if (writer == null && bufferedSize + bytes.length > maxBufferSize) {
            startSpilling();
        }

        if (writer != null) {
            writer.write(MAPPER.writeValueAsString(message));
            writer.newLine();
        } else {
            messages.add(message);
            bufferedSize += bytes.length;
        }

        messageCount++;
    }

    private void startSpilling() throws IOException {
        file = runContext.workingDir().createTempFile();
        writer = Files.newBufferedWriter(
            file,
            StandardCharsets.UTF_8,
            StandardOpenOption.WRITE
        );

        for (String message : messages) {
            writer.write(MAPPER.writeValueAsString(message));
            writer.newLine();
        }

        messages.clear();
        bufferedSize = 0;
    }

    public synchronized URIResult finish() throws IOException {
        if (writer == null) {
            return new URIResult(List.copyOf(messages), null, messageCount);
        }

        writer.close();
        writer = null;

        var uri = runContext.storage().putFile(file.toFile(), "websocket-messages.jsonl");
        return new URIResult(List.of(), uri, messageCount);
    }

    @Override
    public synchronized void close() throws IOException {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }

    @Getter
    public static class URIResult {
        private final List<String> messages;
        private final java.net.URI messagesUri;
        private final long count;

        public URIResult(List<String> messages, java.net.URI messagesUri, long count) {
            this.messages = messages;
            this.messagesUri = messagesUri;
            this.count = count;
        }
    }
}
