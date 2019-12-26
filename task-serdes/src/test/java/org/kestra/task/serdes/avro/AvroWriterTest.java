package org.kestra.task.serdes.avro;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MicronautTest;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunOutput;
import org.kestra.core.storages.StorageInterface;
import org.kestra.core.storages.StorageObject;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class AvroWriterTest {
    @Inject
    StorageInterface storageInterface;

    @Inject
    ApplicationContext applicationContext;

    @Test
    void map() throws Exception {
        test("csv/insurance_sample.javas");
    }

    @Test
    void array() throws Exception {
        test("csv/insurance_sample_array.javas");
    }

    void test(String file) throws Exception {
        StorageObject source = storageInterface.put(
            new URI("/" + FriendlyId.createFriendlyId()),
            new FileInputStream(new File(Objects.requireNonNull(AvroWriterTest.class.getClassLoader()
                .getResource(file))
                .toURI()))
        );

        AvroWriter task = AvroWriter.builder()
            .from(source.getUri().toString())
            .schema(
                Files.asCharSource(
                    new File(Objects.requireNonNull(AvroWriterTest.class.getClassLoader().getResource("csv/insurance_sample.avsc")).toURI()),
                    Charsets.UTF_8
                ).read()
            )
            .build();

        RunOutput run = task.run(new RunContext(this.applicationContext, ImmutableMap.of()));

        assertThat(
            AvroWriterTest.avroSize(this.storageInterface.get((URI) run.getOutputs().get("uri"))),
            is(AvroWriterTest.avroSize(
                new FileInputStream(new File(Objects.requireNonNull(AvroWriterTest.class.getClassLoader()
                    .getResource("csv/insurance_sample.avro"))
                    .toURI())))
            )
        );
    }

    public static int avroSize(InputStream inputStream) throws IOException {
        DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
        DataFileStream<GenericRecord> dataFileReader = new DataFileStream<>(inputStream, datumReader);
        AtomicInteger i = new AtomicInteger();
        dataFileReader.forEach(genericRecord -> i.getAndIncrement());

        return i.get();
    }
}