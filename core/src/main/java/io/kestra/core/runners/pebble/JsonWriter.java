package io.kestra.core.runners.pebble;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.serializers.JacksonMapper;

import io.pebbletemplates.pebble.extension.writer.SpecializedWriter;
import lombok.SneakyThrows;

public class JsonWriter extends OutputWriter implements SpecializedWriter {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private final StringWriter stringWriter = new StringWriter();
    private boolean hasOutput = false;

    @Override
    public void writeSpecialized(int i) {
        hasOutput = true;
        stringWriter.getBuffer().append(i);
    }

    @Override
    public void writeSpecialized(long l) {
        hasOutput = true;
        stringWriter.getBuffer().append(l);
    }

    @Override
    public void writeSpecialized(double d) {
        hasOutput = true;
        stringWriter.getBuffer().append(d);
    }

    @Override
    public void writeSpecialized(float f) {
        hasOutput = true;
        stringWriter.getBuffer().append(f);
    }

    @Override
    public void writeSpecialized(short s) {
        hasOutput = true;
        stringWriter.getBuffer().append(s);
    }

    @Override
    public void writeSpecialized(byte b) {
        hasOutput = true;
        stringWriter.getBuffer().append(b);
    }

    @Override
    public void writeSpecialized(char c) {
        hasOutput = true;
        stringWriter.getBuffer().append(c);
    }

    @Override
    public void writeSpecialized(String s) {
        if (s == null) {
            return;
        }
        hasOutput = true;
        stringWriter.getBuffer().append(s);
    }

    @SneakyThrows
    @Override
    public void write(Object o) {
        if (o == null) {
            return;
        }
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
        if (len > 0) {
            hasOutput = true;
        }
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
        return hasOutput ? this.toString() : null;
    }
}
