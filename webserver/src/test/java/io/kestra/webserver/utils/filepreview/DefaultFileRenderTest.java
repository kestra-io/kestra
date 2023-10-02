package io.kestra.webserver.utils.filepreview;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class DefaultFileRenderTest {
    @ParameterizedTest
    @CsvSource({"0, false", "100, false", "101, true"})
    void testTruncatedByLineCount(int lineCount, boolean truncated) throws IOException {
        final String line = "foo\n";
        StringBuilder contentBuffer = new StringBuilder(lineCount * line.length());

        for (int i = 0; i < lineCount; i++) {
            contentBuffer.append(line);
        }
        InputStream is = new ByteArrayInputStream(contentBuffer.toString().getBytes(StandardCharsets.UTF_8));

        DefaultFileRender render = new DefaultFileRender("txt", is);

        assertThat(render.truncated, is(truncated));
    }

    @ParameterizedTest
    @CsvSource({"0, false", "1024, false", "3072, true"})
    void testTruncatedBySize(int sizeInKibiBytes, boolean truncated) throws IOException {
        final int sizeInBytes = sizeInKibiBytes * 1024;
        StringBuilder contentBuffer = new StringBuilder(sizeInBytes);

        for (int i = 0; i < sizeInBytes; i++) {
            contentBuffer.append("0");
        }
        InputStream is = new ByteArrayInputStream(contentBuffer.toString().getBytes(StandardCharsets.UTF_8));

        DefaultFileRender render = new DefaultFileRender("txt", is);

        assertThat(render.truncated, is(truncated));
    }
}
