package org.floworc.task.avro;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableMap;
import io.micronaut.test.annotation.MicronautTest;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;
import org.floworc.core.storages.StorageInterface;
import org.floworc.core.storages.StorageObject;
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
class CsvToAvroTest {
    @Inject
    StorageInterface storageInterface;

    @Test
    void run() throws Exception {
        StorageObject source = storageInterface.put(
            new URI("/" + FriendlyId.createFriendlyId()),
            new FileInputStream(new File(Objects.requireNonNull(CsvToAvroTest.class.getClassLoader()
                .getResource("csv/insurance_sample.csv"))
                .toURI()))
        );

        CsvToAvro task = CsvToAvro.builder()
            .source(source.getUri())
            .schema(new Schema
                .Parser()
                .parse(new File(Objects.requireNonNull(CsvToAvroTest.class.getClassLoader().getResource("csv/insurance_sample.avsc")).toURI()))
            )
            .fieldSeparator(";".charAt(0))
            .build();

        RunOutput run = task.run(new RunContext(this.storageInterface, ImmutableMap.of()));

        assertThat(
            this.avroSize(this.storageInterface.get((URI) run.getOutputs().get("uri"))),
            is(this.avroSize(
                new FileInputStream(new File(Objects.requireNonNull(CsvToAvroTest.class.getClassLoader()
                .getResource("csv/insurance_sample.avro"))
                .toURI())))
            )
        );
    }

    private int avroSize(InputStream inputStream) throws IOException {
        DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
        DataFileStream<GenericRecord> dataFileReader = new DataFileStream<>(inputStream, datumReader);
        AtomicInteger i = new AtomicInteger();
        dataFileReader.forEach(genericRecord -> i.getAndIncrement());

        return i.get();
    }
}