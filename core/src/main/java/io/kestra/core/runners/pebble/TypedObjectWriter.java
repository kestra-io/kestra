package io.kestra.core.runners.pebble;

import io.pebbletemplates.pebble.extension.writer.SpecializedWriter;
import lombok.SneakyThrows;

import java.io.IOException;
import java.util.Arrays;

public class TypedObjectWriter extends OutputWriter implements SpecializedWriter {
    private Object current;

    @Override
    public void writeSpecialized(int i) {
        concatSpecialized(i);
    }

    @Override
    public void writeSpecialized(long l) {
        concatSpecialized(l);
    }

    @Override
    public void writeSpecialized(double d) {
        concatSpecialized(d);
    }

    @Override
    public void writeSpecialized(float f) {
        concatSpecialized(f);
    }

    @Override
    public void writeSpecialized(short s) {
        concatSpecialized(s);
    }

    @Override
    public void writeSpecialized(byte b) {
        concatSpecialized(b);
    }

    @Override
    public void writeSpecialized(char c) {
        concatSpecialized(c);
    }

    @Override
    public void writeSpecialized(String s) {
        concatSpecialized(s);
    }

    private void concatSpecialized(final Object o) {
        if (o == null) {
            return;
        }

        if (current == null) {
            current = o;
            return;
        }

        if (isConcatenableScalar(current) && isConcatenableScalar(o)) {
            current = current.toString() + o;
            return;
        }

        throw new IllegalArgumentException(
            "Cannot concat " + current.getClass().getName() + " with " + o.getClass().getName()
        );
    }

    @SneakyThrows
    @Override
    public void write(Object o) {
        if (o == null) {
            return;
        }

        if (current == null) {
            current = o;
        } else if (isConcatenableScalar(o)) {
            // do call the writeSpecialized method depending on the object type.
            SpecializedWriter.super.write(o);
        } else {
            throw new IllegalArgumentException(
                "Cannot concat " + current.getClass().getName() + " with " + o.getClass().getName()
            );
        }
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        writeSpecialized(String.valueOf(Arrays.copyOfRange(cbuf, off, off + len)));
    }

    @Override
    public void flush() throws IOException {
        // no-op
    }

    @Override
    public void close() throws IOException {
        // no-op
    }

    @Override
    public Object output() {
        return this.current;
    }

    public static boolean isConcatenableScalar(final Object obj) {
        if (obj == null) return false;

        Class<?> clazz = obj.getClass();

        return clazz == Boolean.class ||
            clazz == Character.class ||
            clazz == String.class ||
            Number.class.isAssignableFrom(clazz);
    }
}
