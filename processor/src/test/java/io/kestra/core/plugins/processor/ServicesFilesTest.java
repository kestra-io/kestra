package io.kestra.core.plugins.processor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.Processor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

class ServicesFilesTest {

    @Test
    void shouldReadServiceFileFromMetaInf() throws IOException {
        String path = ServicesFiles.getPath(Processor.class.getCanonicalName());
        InputStream inputStream = ServicesFilesTest.class.getClassLoader().getResourceAsStream(path);
        Set<String> providers = ServicesFiles.readServiceFile(inputStream);
        Assertions.assertEquals(Set.of(PluginProcessor.class.getCanonicalName()), providers);
    }
}