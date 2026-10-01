package io.kestra.core.models;

import org.junit.jupiter.api.Test;

import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.FormFieldMetadata;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HasSourceTest {

    @Test
    void shouldThrowIllegalArgumentWhenImportingFileWithoutExtension() throws Exception {
        // Given a file whose name has no extension
        CompletedFileUpload fileUpload = CompletedFileUpload.ofMemory(
            new FormFieldMetadata("file", "no-extension", null),
            ReadBufferFactory.getJdkFactory().adapt(new byte[0])
        );

        // When - Then the unsupported type is reported as an IllegalArgumentException
        // instead of a StringIndexOutOfBoundsException raised by substring(-1)
        assertThatThrownBy(() -> HasSource.readSourceFile(null, fileUpload, (source, name) ->
        {
        }))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot import file of type");
    }
}
