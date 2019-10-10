package org.floworc.task.avro;

import org.apache.avro.Schema;
import org.floworc.core.runners.RunContext;
import org.junit.jupiter.api.Test;

import java.io.File;

class CsvToAvroTest {
    @Test
    void run() throws Exception {
        CsvToAvro bash = new CsvToAvro(
            new File(CsvToAvroTest.class.getClassLoader().getResource("csv/insurance_sample.csv").toURI()),
            new Schema.Parser().parse(new File(CsvToAvroTest.class.getClassLoader().getResource("csv/insurance_sample.avsc").toURI())),
            true,
            ";".charAt(0),
            "\"".charAt(0),
            true
        );
        bash.run(new RunContext());
    }
}