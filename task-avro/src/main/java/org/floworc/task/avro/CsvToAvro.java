package org.floworc.task.avro;

import com.google.common.collect.ImmutableMap;
import de.siegmar.fastcsv.reader.CsvParser;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import lombok.*;
import lombok.experimental.SuperBuilder;
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

import javax.validation.constraints.NotNull;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.URI;

@SuperBuilder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Value
@Slf4j
public class CsvToAvro extends Task implements RunnableTask {
    @NotNull
    private URI source;

    @NotNull
    private Schema schema;

    @Builder.Default
    private Boolean header = true;

    @Builder.Default
    private Character fieldSeparator = ",".charAt(0);

    @Builder.Default
    private Character textDelimiter = "\"".charAt(0);

    @Builder.Default
    private Boolean skipEmptyRows = false;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        // reader
        CsvReader csvReader = this.csvReader();
        CsvParser csvParser = csvReader.parse(new InputStreamReader(runContext.uriToInputStream(source)));

        // temp file
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".avro");
        BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile));

        // avro writer
        DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema);
        DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter);
        dataFileWriter.create(schema, output);

        // convert
        Flowable<GenericData.Record> flowable = Flowable
            .create(this.nextRow(csvParser), BackpressureStrategy.BUFFER)
            .observeOn(Schedulers.computation())
            .map(this.convertToAvro())
            .observeOn(Schedulers.io())
            .doOnNext(dataFileWriter::append)
            .doOnComplete(() -> {
                dataFileWriter.close();
                csvParser.close();
            });

        // metrics & finalize
        Single<Long> count = flowable.count();
        Long lineCount = count.blockingGet();

        return RunOutput.builder()
            .outputs(ImmutableMap.of("uri", runContext.putFile(tempFile).getUri()))
            .build();
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

    private CsvReader csvReader() {
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