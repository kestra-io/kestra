package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
class FileInputTest {

    @Test
    void shouldGetExtensionWhenFindingFileExtensionForExistingFile() {
        List<Input<?>> inputs = List.of(
            FileInput.builder().id("test-file1").extension(".zip").build(),
            FileInput.builder().id("test-file2").extension(".gz").build()
        );

        String result = FileInput.findFileInputExtension(inputs, "test-file1");
        Assertions.assertEquals(".zip", result);
    }

    @Test
    void shouldReturnDefaultExtensionWhenFindingExtensionForUnknownFile() {
        List<Input<?>> inputs = List.of(
            FileInput.builder().id("test-file1").extension(".zip").build(),
            FileInput.builder().id("test-file2").extension(".gz").build()
        );

        String result = FileInput.findFileInputExtension(inputs, "???");
        Assertions.assertEquals(".upl", result);
    }
}