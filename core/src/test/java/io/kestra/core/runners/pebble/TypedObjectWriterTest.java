package io.kestra.core.runners.pebble;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TypedObjectWriterTest {

    @Test
    void writeInt() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized(1);
            assertThat(writer.output()).isEqualTo(1);
        }
    }

    @Test
    void writeInts() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized(1);
            writer.writeSpecialized(2);
            writer.writeSpecialized(3);
            assertThat(writer.output()).isEqualTo("123");
        }
    }

    @Test
    void writeLong() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized(1L);
            assertThat(writer.output()).isEqualTo(1L);
        }
    }

    @Test
    void writeLongs() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized(1L);
            writer.writeSpecialized(2L);
            writer.writeSpecialized(3L);
            assertThat(writer.output()).isEqualTo("123");
        }
    }

    @Test
    void writeDouble() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized(1.0);
            assertThat(writer.output()).isEqualTo(1.0);
        }
    }

    @Test
    void writeDoubles() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized(1.0);
            writer.writeSpecialized(2.0);
            writer.writeSpecialized(3.0);
            assertThat(writer.output()).isEqualTo("1.02.03.0");
        }
    }

    @Test
    void writeFloat() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized(1.0f);
            assertThat(writer.output()).isEqualTo(1.0f);
        }
    }

    @Test
    void writeFloats() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized(1.0f);
            writer.writeSpecialized(2.0f);
            writer.writeSpecialized(3.0f);
            assertThat(writer.output()).isEqualTo("1.02.03.0");
        }
    }

    @Test
    void writeShort() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized((short) 1);
            assertThat(writer.output()).isEqualTo((short) 1);
        }
    }

    @Test
    void writeShorts() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized((short) 1);
            writer.writeSpecialized((short) 2);
            writer.writeSpecialized((short) 3);
            assertThat(writer.output()).isEqualTo("123");
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
            assertThat(writer.output()).isEqualTo("979899");
        }
    }

    @Test
    void writeChars() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized('a');
            writer.writeSpecialized('b');
            writer.writeSpecialized('c');
            assertThat(writer.output()).isEqualTo("abc");
        }
    }

    @Test
    void writeStrings() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.writeSpecialized("a");
            writer.writeSpecialized("b");
            writer.writeSpecialized("c");
            assertThat(writer.output()).isEqualTo("abc");
        }
    }

    @Test
    void writeObjects() throws IOException {
        try (TypedObjectWriter writer = new TypedObjectWriter()){
            writer.write(Map.of("a", "b"));
            IllegalArgumentException illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () -> writer.write(Map.of("c", "d")));
            assertThat(illegalArgumentException.getMessage()).isEqualTo("Cannot concat java.util.ImmutableCollections$Map1 with java.util.ImmutableCollections$Map1");
        }
    }
}
