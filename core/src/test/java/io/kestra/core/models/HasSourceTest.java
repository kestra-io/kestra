package io.kestra.core.models;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import io.micronaut.http.multipart.CompletedFileUpload;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HasSourceTest {

    @Test
    void shouldThrowIllegalArgumentWhenImportingFileWithoutExtension() throws Exception {
        // Given a file whose name has no extension
        CompletedFileUpload fileUpload = mock(CompletedFileUpload.class);
        when(fileUpload.getFilename()).thenReturn("no-extension");
        when(fileUpload.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));

        // When - Then the unsupported type is reported as an IllegalArgumentException
        // instead of a StringIndexOutOfBoundsException raised by substring(-1)
        assertThatThrownBy(() -> HasSource.readSourceFile(null, fileUpload, (source, name) ->
        {
        }))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot import file of type");
    }
}
