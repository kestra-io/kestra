package io.kestra.webserver.utils.filepreview;

import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.GroupWriteSupport;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;
import org.junit.jupiter.api.Test;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ParquetFileRenderTest {

    private byte[] createSimpleParquetFile() throws IOException {
        String schemaString = "message test { required binary name (UTF8); required int32 age; }";
        MessageType schema = MessageTypeParser.parseMessageType(schemaString);

        File tempFile = File.createTempFile("test", ".parquet");
        tempFile.delete();
        tempFile.deleteOnExit();

        GroupWriteSupport.setSchema(schema, new org.apache.hadoop.conf.Configuration());
        try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(new org.apache.hadoop.fs.Path(tempFile.getAbsolutePath()))
                .withType(schema)
                .build()) {
            Group group = new org.apache.parquet.example.data.simple.SimpleGroup(schema);
            group.add("name", "Alice");
            group.add("age", 30);
            writer.write(group);

            group = new org.apache.parquet.example.data.simple.SimpleGroup(schema);
            group.add("name", "Bob");
            group.add("age", 25);
            writer.write(group);
        }

        try (InputStream in = new FileInputStream(tempFile)) {
            return in.readAllBytes();
        }
    }

    @Test
    void testRenderContent_ListType() throws IOException {
        byte[] parquetData = createSimpleParquetFile();
        InputStream inputStream = new ByteArrayInputStream(parquetData);

        ParquetFileRender render = new ParquetFileRender("parquet", inputStream, 10);

        assertEquals(FileRender.Type.LIST, render.getType());
        assertFalse(render.isTruncated());
        assertNotNull(render.getContent());
        assertTrue(render.getContent() instanceof List);

        List<?> content = (List<?>) render.getContent();
        assertEquals(2, content.size());

        Map<?, ?> row1 = (Map<?, ?>) content.get(0);
        assertEquals("Alice", row1.get("name"));
        assertEquals("30", row1.get("age").toString());

        Map<?, ?> row2 = (Map<?, ?>) content.get(1);
        assertEquals("Bob", row2.get("name"));
        assertEquals("25", row2.get("age").toString());
    }

    @Test
    void testRenderContent_Truncated() throws IOException {
        byte[] parquetData = createSimpleParquetFile();
        InputStream inputStream = new ByteArrayInputStream(parquetData);

        ParquetFileRender render = new ParquetFileRender("parquet", inputStream, 1);

        assertTrue(render.isTruncated());
        assertNotNull(render.getContent());
        assertTrue(render.getContent() instanceof List);

        List<?> content = (List<?>) render.getContent();
        assertEquals(1, content.size());
    }

    @Test
    void testInMemorySeekableInputStream() throws IOException {
        byte[] data = "testdata".getBytes(StandardCharsets.UTF_8);
        ParquetFileRender.InMemorySeekableInputStream stream = new ParquetFileRender.InMemorySeekableInputStream(data);

        assertEquals(0, stream.getPos());
        assertEquals('t', stream.read());
        assertEquals(1, stream.getPos());

        stream.seek(4);
        assertEquals(4, stream.getPos());
        assertEquals('d', stream.read());

        byte[] buffer = new byte[4];
        stream.seek(0);
        int read = stream.read(buffer, 0, 4);
        assertEquals(4, read);
        assertArrayEquals("test".getBytes(StandardCharsets.UTF_8), buffer);
    }
}
