package io.kestra.core.runners;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.*;
import org.apache.commons.lang3.NotImplementedException;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Optional;

public class TestStreamingFileUpload implements StreamingFileUpload {
    private final String name;
    private final MediaType contentType;
    private final byte[] data;

    public TestStreamingFileUpload(String name, byte[] data, @Nullable MediaType contentType) {
        this.name = name;
        this.contentType = contentType;
        this.data = data;
    }

    @Override
    public Publisher<Boolean> transferTo(String location) {
        throw new NotImplementedException("transferTo(String location) is not implemented");
    }

    @SuppressWarnings("BlockingMethodInNonBlockingContext")
    @Override
    public Publisher<Boolean> transferTo(File destination) {
        return Flux.create(fluxSink -> {
            try {
                Files.write(destination.toPath(), this.data);
                fluxSink.next(true);
            } catch (IOException e) {
                fluxSink.error(e);
            }
        });
    }

    @Override
    public Publisher<Boolean> delete() {
        throw new NotImplementedException("delete() is not implemented");
    }

    @Override
    public Optional<MediaType> getContentType() {
        return Optional.ofNullable(this.contentType);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getFilename() {
        return this.name;
    }

    @Override
    public long getSize() {
        return this.data.length;
    }

    @Override
    public long getDefinedSize() {
        return this.data.length;
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void subscribe(Subscriber<? super PartData> s) {
        s.onNext(new CompletedFileUpload(
            this.name,
            this.data,
            this.contentType
        ));
    }

    public static class CompletedFileUpload implements io.micronaut.http.multipart.CompletedFileUpload {
        private final String name;
        private final MediaType contentType;
        private final byte[] data;

        public CompletedFileUpload(String name, byte[] data, @Nullable MediaType contentType) {
            this.name = name;
            this.contentType = contentType;
            this.data = data;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(this.data);
        }

        @Override
        public byte[] getBytes() throws IOException {
            return this.data;
        }

        @Override
        public ByteBuffer getByteBuffer() throws IOException {
            return ByteBuffer.wrap(this.data);
        }

        @Override
        public Optional<MediaType> getContentType() {
            return Optional.ofNullable(this.contentType);
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public String getFilename() {
            return this.name;
        }

        @Override
        public long getSize() {
            return this.data.length;
        }

        @Override
        public long getDefinedSize() {
            return this.data.length;
        }

        @Override
        public boolean isComplete() {
            return true;
        }
    }
}
