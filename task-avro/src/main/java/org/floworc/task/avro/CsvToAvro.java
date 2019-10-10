package org.floworc.task.avro;

import de.siegmar.fastcsv.reader.CsvParser;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.floworc.core.models.tasks.RunnableTask;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;
import org.floworc.core.storages.StorageObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Value
@Slf4j
public class CsvToAvro extends Task implements RunnableTask {
    private File source;
    private Schema schema;

    private Boolean header;
    private Character fieldSeparator;
    private Character textDelimiter;
    private Boolean skipEmptyRows;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        CsvReader csvReader = this.getReader();
        CsvParser csvParser = csvReader.parse(source, StandardCharsets.UTF_8);

        BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream("fileName"));
        DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema);
        DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter);
        dataFileWriter.create(schema, output);

        //noinspection ResultOfMethodCallIgnored
        Flowable
            .create(this.nextRow(csvParser), BackpressureStrategy.BUFFER)
            .observeOn(Schedulers.computation())
            .map(this.convertToAvro())
            .observeOn(Schedulers.io())
            .subscribe(
                dataFileWriter::append,
                error -> {
                    throw new RuntimeException(error);
                },
                () -> {
                    dataFileWriter.close();
                    csvParser.close();
                }
            );

        return null;
    }

    private Function<CsvRow, GenericData.Record> convertToAvro() {
        return row -> {
            GenericData.Record record = new GenericData.Record(schema);

            for (Schema.Field field : schema.getFields()) {
                record.put(field.name(), row.getField(field.name()));
            }

            return record;
        };
    }

    private FlowableOnSubscribe<CsvRow> nextRow(CsvParser csvParser) {
        return s -> {
            CsvRow row;
            while ((row = csvParser.nextRow()) != null) {
                s.onNext(row);
            }

            s.onComplete();
        };
    }

    private CsvReader getReader() {
        CsvReader csvReader = new CsvReader();

        if (this.header != null) {
            csvReader.setContainsHeader(this.header);
        }

        if (this.textDelimiter != null) {
            csvReader.setTextDelimiter(textDelimiter);
        }

        if (this.fieldSeparator != null) {
            csvReader.setFieldSeparator(fieldSeparator);
        }

        if (this.skipEmptyRows != null) {
            csvReader.setSkipEmptyRows(skipEmptyRows);
        }

        return csvReader;
    }
}