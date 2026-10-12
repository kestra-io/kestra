package io.kestra.controller.grpc.streaming;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.protobuf.ByteString;

import lombok.extern.slf4j.Slf4j;

/**
 * Collects the chunks of a payload received over a client-streaming call, holding at most one chunk in memory.
 * <p>
 * Most payloads fit in a single chunk, so a temporary file is only created once a second chunk arrives.
 * Closing the spool deletes that file.
 */
@Slf4j
public final class ChunkSpool implements Closeable {

    private final String prefix;
    private ByteString firstChunk;
    private Path spool;
    private OutputStream spoolOutput;
    private InputStream spoolInput;

    public ChunkSpool(String prefix) {
        this.prefix = prefix;
    }

    public void append(ByteString chunk) throws IOException {
        if (spoolOutput == null && firstChunk == null) {
            firstChunk = chunk;
            return;
        }
        if (spoolOutput == null) {
            spool = Files.createTempFile(prefix, ".part");
            spoolOutput = new BufferedOutputStream(Files.newOutputStream(spool));
            firstChunk.writeTo(spoolOutput);
            firstChunk = null;
        }
        chunk.writeTo(spoolOutput);
    }

    /**
     * @return the payload received so far, readable until the spool is closed, which also closes it.
     */
    public InputStream content() throws IOException {
        if (spoolOutput == null) {
            return (firstChunk == null ? ByteString.EMPTY : firstChunk).newInput();
        }
        spoolOutput.close();
        spoolInput = Files.newInputStream(spool);
        return spoolInput;
    }

    @Override
    public void close() {
        try {
            if (spoolInput != null) {
                spoolInput.close();
            }
            if (spoolOutput != null) {
                spoolOutput.close();
            }
            if (spool != null) {
                Files.deleteIfExists(spool);
            }
        } catch (IOException e) {
            log.warn("Failed to delete the spooled payload '{}'", spool, e);
        }
    }
}
