package io.kestra.core.runners.pebble;

import io.pebbletemplates.pebble.extension.writer.SpecializedWriter;
import lombok.SneakyThrows;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.IntStream;

public class TypedObjectWriter extends OutputWriter implements SpecializedWriter {
    private Object current;

    @Override
    public void writeSpecialized(int i) {
        if (current == null) {
            current = i;
        } else {
            Integer currentI = this.ofSameTypeOrThrow(current, Integer.class);
            current = currentI + i;
        }
    }

    @Override
    public void writeSpecialized(long l) {
        if (current == null) {
            current = l;
        } else {
            Long currentL = this.ofSameTypeOrThrow(current, Long.class);
            current = currentL + l;
        }
    }

    @Override
    public void writeSpecialized(double d) {
        if (current == null) {
            current = d;
        } else {
            Double currentD = this.ofSameTypeOrThrow(current, Double.class);
            current = currentD + d;
        }
    }

    @Override
    public void writeSpecialized(float f) {
        if (current == null) {
            current = f;
        } else {
            Float currentF = this.ofSameTypeOrThrow(current, Float.class);
            current = currentF + f;
        }
    }

    @Override
    public void writeSpecialized(short s) {
        if (current == null) {
            current = s;
        } else {
            Short currentS = null;
            try {
                currentS = this.ofSameTypeOrThrow(current, Short.class);
            } catch (Exception e) {
                try {
                    current = this.ofSameTypeOrThrow(current, Integer.class) + s;
                    return;
                } catch (Exception ex) {
                    throw e;
                }
            }
            current = currentS + s;
        }
    }

    @Override
    public void writeSpecialized(byte b) {
        if (current == null) {
            current = b;
        } else {
            Byte currentB = null;
            try {
                currentB = this.ofSameTypeOrThrow(current, Byte.class);
            } catch (Exception e) {
                try {
                    current = this.ofSameTypeOrThrow(current, Integer.class) + b;
                    return;
                } catch (Exception ex) {
                    throw e;
                }
            }
            current = currentB + b;
        }
    }

    @Override
    public void writeSpecialized(char c) {
        if (current == null) {
            current = c;
        } else {
            Character currentC;
            try {
                currentC = this.ofSameTypeOrThrow(current, Character.class);
            } catch (Exception e) {
                try {
                    current = this.ofSameTypeOrThrow(current, String.class) + c;
                    return;
                } catch (Exception ex) {
                    throw e;
                }
            }
            current = "" + currentC + c;
        }
    }

    @Override
    public void writeSpecialized(String s) {
        if (current == null) {
            current = s;
        } else {
            String currentS = this.ofSameTypeOrThrow(current, String.class);
            current = currentS + s;
        }
    }

    @SneakyThrows
    @Override
    public void write(Object o) {
        if (current == null) {
            current = o;
        } else {
            throwIllegalAddition(current, o.getClass());
        }
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        if (current == null) {
            current = String.valueOf(Arrays.copyOfRange(cbuf, off, off + len));
            return;
        }

        if (current instanceof String) {
            current += String.valueOf(Arrays.copyOfRange(cbuf, off, off + len));
        } else {
            for (int idx = off; idx < off + len; idx++) {
                this.writeSpecialized(cbuf[idx]);
            }
        }
    }

    @Override
    public void flush() throws IOException {
        // no-op
    }

    @Override
    public void close() throws IOException {
        // no-op
    }

    private <T> T ofSameTypeOrThrow(Object baseObject, Class<T> clazz) {
        if (clazz.isAssignableFrom(baseObject.getClass())) {
            return clazz.cast(baseObject);
        } else {
            throwIllegalAddition(baseObject, clazz);
            return null;
        }
    }

    private static <T> void throwIllegalAddition(Object baseObject, Class<T> clazz) {
        throw new IllegalArgumentException("Tried to add " + clazz.getName() + " to " + baseObject.getClass().getName());
    }

    @Override
    public Object output() {
        return this.current;
    }
}
