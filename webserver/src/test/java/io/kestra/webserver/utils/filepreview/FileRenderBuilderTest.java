package io.kestra.webserver.utils.filepreview;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FileRenderBuilderTest {
    @ParameterizedTest
    @MethodSource("provideExtensions")
    void of(String extension, Class returnedClass) throws IOException {
        var emptyInput = new ByteArrayInputStream("".getBytes());
        var charset = StandardCharsets.UTF_8;

        assertThat(
            FileRenderBuilder.of(extension, emptyInput, Optional.of(charset), 1000).getClass(),
            is(returnedClass)
        );
    }

    private static Stream<Arguments> provideExtensions() {
        return Stream.of(
            Arguments.of("ion", IonFileRender.class),
            Arguments.of("md", DefaultFileRender.class),
            Arguments.of("pdf", PdfFileRender.class),
            Arguments.of("PDF", PdfFileRender.class),
            Arguments.of("foobar", DefaultFileRender.class)
        );
    }
}
