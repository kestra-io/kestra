package io.kestra.webserver.utils.filepreview;

import io.kestra.core.serializers.FileSerde;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.*;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class IonFileRenderTest {
    @ParameterizedTest
    @CsvSource({"0, false", "100, false", "101, true"})
    void testTruncatedByLineCount(int lineCount, boolean truncated) throws IOException {
        File tempFile = File.createTempFile("unit", ".ion");

        try (OutputStream output = new FileOutputStream(tempFile);) {
            for (int i = 0; i < lineCount; i++) {
                FileSerde.write(output, Map.of(1, 2));
            }
        }

        final InputStream is = new DataInputStream(new FileInputStream(tempFile));
        IonFileRender render = new IonFileRender("ion", is);

        assertThat(render.truncated, is(truncated));
    }
}