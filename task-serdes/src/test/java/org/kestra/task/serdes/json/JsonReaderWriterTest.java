package org.kestra.task.serdes.json;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MicronautTest;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunOutput;
import org.kestra.core.storages.StorageInterface;
import org.kestra.core.storages.StorageObject;
import org.kestra.task.serdes.SerdesUtils;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class JsonReaderWriterTest {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    StorageInterface storageInterface;

    @Inject
    SerdesUtils serdesUtils;

    @Test
    void run() throws Exception {
        File sourceFile = SerdesUtils.resourceToFile("csv/full.jsonl");
        StorageObject source = this.serdesUtils.resourceToStorageObject(sourceFile);

        JsonReader reader = JsonReader.builder()
            .from(source.getUri().toString())
            .build();
        RunOutput readerRunOutput = reader.run(new RunContext(this.applicationContext, ImmutableMap.of()));

        JsonWriter writer = JsonWriter.builder()
            .from(readerRunOutput.getOutputs().get("uri").toString())
            .build();
        RunOutput writerRunOutput = writer.run(new RunContext(this.applicationContext, ImmutableMap.of()));

        assertThat(
            CharStreams.toString(new InputStreamReader(storageInterface.get((URI) writerRunOutput.getOutputs().get("uri")))),
            is(CharStreams.toString(new InputStreamReader(new FileInputStream(sourceFile))))
        );
    }
}