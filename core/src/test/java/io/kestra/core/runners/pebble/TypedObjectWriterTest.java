package io.kestra.core.runners.pebble;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TypedObjectWriterTest {
    @Test
    void invalidAddition() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized(1);
            IllegalArgumentException illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () -> writer.writeSpecialized('a'));
            assertThat(illegalArgumentException.getMessage(), is("Tried to add java.lang.Character to java.lang.Integer"));
        }
    }

    @Test
    void writeInts() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized(1);
            writer.writeSpecialized(2);
            writer.writeSpecialized(3);
            assertThat(writer.output(), is(6));
        }
    }

    @Test
    void writeLongs() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized(1L);
            writer.writeSpecialized(2L);
            writer.writeSpecialized(3L);
            assertThat(writer.output(), is(6L));
        }
    }

    @Test
    void writeDoubles() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized(1.0);
            writer.writeSpecialized(2.0);
            writer.writeSpecialized(3.0);
            assertThat(writer.output(), is(6.0));
        }
    }

    @Test
    void writeFloats() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized(1.0f);
            writer.writeSpecialized(2.0f);
            writer.writeSpecialized(3.0f);
            assertThat(writer.output(), is(6.0f));
        }
    }

    @Test
    void writeShorts() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized((short) 1);
            writer.writeSpecialized((short) 2);
            writer.writeSpecialized((short) 3);
            assertThat(writer.output(), is(6));
        }
    }

    @Test
    void writeBytes() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            byte aByte = "a".getBytes()[0];
            writer.writeSpecialized(aByte);
            byte bByte = "b".getBytes()[0];
            writer.writeSpecialized(bByte);
            byte cByte = "c".getBytes()[0];
            writer.writeSpecialized(cByte);
            assertThat(writer.output(), is((aByte + bByte) + cByte));
        }
    }

    @Test
    void writeChars() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized('a');
            writer.writeSpecialized('b');
            writer.writeSpecialized('c');
            assertThat(writer.output(), is("abc"));
        }
    }

    @Test
    void writeStrings() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized("a");
            writer.writeSpecialized("b");
            writer.writeSpecialized("c");
            assertThat(writer.output(), is("abc"));
        }
    }

    @Test
    void writeObjects() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.write(Map.of("a", "b"));
            IllegalArgumentException illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () -> writer.write(Map.of("c", "d")));
            assertThat(illegalArgumentException.getMessage(), is("Tried to add java.util.ImmutableCollections$Map1 to java.util.ImmutableCollections$Map1"));
        }
    }
}
