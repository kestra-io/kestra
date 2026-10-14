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

    public JsonWriter() {
        super();
    }

    public JsonWriter(final long maxOutputSize) {
        super(maxOutputSize);
    }

    /**
     * Flags that output was produced and enforces the output-size limit against the current buffer.
     * Called after every append so unbounded accumulation is stopped before it can exhaust the heap.
     */
    private void afterAppend() {
        hasOutput = true;
        checkOutputSize(stringWriter.getBuffer().length());
    }

    @Override
    public void writeSpecialized(int i) {
        stringWriter.getBuffer().append(i);
        afterAppend();
    }

    @Override
    public void writeSpecialized(long l) {
        stringWriter.getBuffer().append(l);
        afterAppend();
    }

    @Override
    public void writeSpecialized(double d) {
        stringWriter.getBuffer().append(d);
        afterAppend();
    }

    @Override
    public void writeSpecialized(float f) {
        stringWriter.getBuffer().append(f);
        afterAppend();
    }

    @Override
    public void writeSpecialized(short s) {
        stringWriter.getBuffer().append(s);
        afterAppend();
    }

    @Override
    public void writeSpecialized(byte b) {
        stringWriter.getBuffer().append(b);
        afterAppend();
    }

    @Override
    public void writeSpecialized(char c) {
        stringWriter.getBuffer().append(c);
        afterAppend();
    }

    @Override
    public void writeSpecialized(String s) {
        if (s == null) {
            return;
        }
        stringWriter.getBuffer().append(s);
        afterAppend();
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
        this.stringWriter.write(cbuf, off, len);
        if (len > 0) {
            afterAppend();
        }
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
