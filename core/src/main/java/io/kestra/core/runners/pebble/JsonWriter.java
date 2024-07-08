package io.kestra.core.runners.pebble;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pebbletemplates.pebble.extension.writer.SpecializedWriter;
import io.kestra.core.serializers.JacksonMapper;
import lombok.SneakyThrows;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;

public class JsonWriter extends OutputWriter implements SpecializedWriter {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private final StringWriter stringWriter = new StringWriter();

    @Override
    public void writeSpecialized(int i) {
        stringWriter.getBuffer().append(i);
    }

    @Override
    public void writeSpecialized(long l) {
        stringWriter.getBuffer().append(l);
    }

    @Override
    public void writeSpecialized(double d) {
        stringWriter.getBuffer().append(d);
    }

    @Override
    public void writeSpecialized(float f) {
        stringWriter.getBuffer().append(f);
    }

    @Override
    public void writeSpecialized(short s) {
        stringWriter.getBuffer().append(s);
    }

    @Override
    public void writeSpecialized(byte b) {
        stringWriter.getBuffer().append(b);
    }

    @Override
    public void writeSpecialized(char c) {
        stringWriter.getBuffer().append(c);
    }

    @Override
    public void writeSpecialized(String s) {
        stringWriter.getBuffer().append(s);
    }

    @SneakyThrows
    @Override
    public void write(Object o) {
        if (o instanceof Map) {
            writeSpecialized(MAPPER.writeValueAsString(o));
        } else if (o instanceof Collection) {
            writeSpecialized(MAPPER.writeValueAsString(o));
        } else if (o.getClass().isArray()) {
            writeSpecialized(MAPPER.writeValueAsString(o));
        } else {
            SpecializedWriter.super.write(o);
        }
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        this.stringWriter.write(cbuf, off, len);
    }

    @Override
    public void flush() throws IOException {
        this.stringWriter.flush();
    }

    @Override
    public void close() throws IOException {
        this.stringWriter.flush();
    }

    @Override
    public String toString() {
        return stringWriter.toString();
    }

    @Override
    public Object output() {
        return this.toString();
    }
}
