package io.kestra.core.models.flows.input;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.Input;

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