package io.kestra.core.plugins.processor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.Processor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

class ServicesFilesTest {

    @Test
    void shouldReadServiceFileFromMetaInf() throws IOException {
        String path = ServicesFiles.getPath(Processor.class.getCanonicalName());
        InputStream inputStream = ServicesFilesTest.class.getClassLoader().getResourceAsStream(path);
        Set<String> providers = ServicesFiles.readServiceFile(inputStream);
        Assertions.assertEquals(Set.of(PluginProcessor.class.getCanonicalName()), providers);
    }

    @Test
    void testWriteAndReadServiceFileRoundTrip() throws IOException {
        Set<String> services = Set.of("com.example.ServiceA", "com.example.ServiceB");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ServicesFiles.writeServiceFile(services, out);

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Set<String> read = ServicesFiles.readServiceFile(in);

        Assertions.assertEquals(services, read);
    }

    @Test
    void testReadServiceFileWithCommentsAndWhitespace() throws IOException {
        String content = """
        # comment
        com.example.ServiceA

        com.example.ServiceB   # inline comment
        """;
        ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        Set<String> read = ServicesFiles.readServiceFile(in);

        Assertions.assertEquals(Set.of("com.example.ServiceA", "com.example.ServiceB"), read);
    }
    @Test
    void testReadServiceFileWithDuplicates() throws IOException {
        String content = """
        com.example.ServiceA
        com.example.ServiceA
        com.example.ServiceB
        """;
        ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        Set<String> read = ServicesFiles.readServiceFile(in);

        Assertions.assertEquals(Set.of("com.example.ServiceA", "com.example.ServiceB"), read);
    }

    @Test
    void testWriteEmptyServiceFile() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ServicesFiles.writeServiceFile(Set.of(), out);
        Assertions.assertEquals("", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void testReadEmptyServiceFile() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        Set<String> read = ServicesFiles.readServiceFile(in);
        Assertions.assertTrue(read.isEmpty());
    }

    @Test
    void testReadMalformedServiceFile() throws IOException {
        String content = "# only a comment\n\n";
        ByteArrayInputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        Set<String> read = ServicesFiles.readServiceFile(in);
        Assertions.assertTrue(read.isEmpty());
    }
}
