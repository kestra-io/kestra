package org.kestra.task.serdes.avro;

import com.google.common.collect.ImmutableMap;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunOutput;
import org.kestra.core.serializers.ObjectsSerde;
import org.slf4j.Logger;

import javax.validation.constraints.NotNull;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.net.URI;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class AvroWriter extends Task implements RunnableTask {
    @NotNull
    private String from;

    @NotNull
    private String schema;

    private List<String> trueValues;

    private List<String> falseValues;

    private List<String> nullValues;

    private String dateFormat;

    private String timeFormat;

    private String datetimeFormat;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger(this.getClass());

        // temp file
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".avro");
        BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile));

        // avro writer
        Schema.Parser parser = new Schema.Parser();
        Schema schema = parser.parse(this.schema);

        DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema, AvroConverter.genericData());
        DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<>(datumWriter);
        dataFileWriter.create(schema, output);

        // reader
        URI from = new URI(runContext.render(this.from));
        ObjectInputStream inputStream = new ObjectInputStream(runContext.uriToInputStream(from));

        // convert
        Flowable<GenericData.Record> flowable = Flowable
            .create(ObjectsSerde.reader(inputStream), BackpressureStrategy.BUFFER)
            .observeOn(Schedulers.computation())
            .map(this.convertToAvro(schema))
            .observeOn(Schedulers.io())
            .doOnNext(datum -> {
                try {
                    dataFileWriter.append(datum);
                } catch (Throwable e) {
                    throw new AvroConverter.IllegalRowConvertion(
                        datum.getSchema()
                            .getFields()
                            .stream()
                            .map(field -> new AbstractMap.SimpleEntry<>(field.name(), datum.get(field.name())))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                        e
                    );
                }
            })
            .doOnComplete(() -> {
                dataFileWriter.close();
                inputStream.close();
                output.close();
            });

        // metrics & finalize
        Single<Long> count = flowable.count();
        Long lineCount = count.blockingGet();

        return RunOutput.builder()
            .outputs(ImmutableMap.of("uri", runContext.putFile(tempFile).getUri()))
            .build();
    }

    @SuppressWarnings("unchecked")
    private Function<Object, GenericData.Record> convertToAvro(Schema schema) {
        AvroConverter converter = this.converter();

        return row -> {
            GenericData.Record record = new GenericData.Record(schema);

            if (row instanceof List) {
                List<String> casted = (List<String>) row;

                return converter.fromArray(schema, casted);
            } else if (row instanceof Map) {
                Map<String, Object> casted = (Map<String, Object>) row;

                return converter.fromMap(schema, casted);
            }

            return record;
        };
    }

    private AvroConverter converter() {
        AvroConverter.AvroConverterBuilder builder = AvroConverter.builder();

        if (this.trueValues != null) {
            builder.trueValues(this.trueValues);
        }

        if (this.falseValues != null) {
            builder.falseValues(this.falseValues);
        }

        if (this.nullValues != null) {
            builder.nullValues(this.nullValues);
        }

        if (this.dateFormat != null) {
            builder.dateFormat(this.dateFormat);
        }

        if (this.timeFormat != null) {
            builder.timeFormat(this.timeFormat);
        }

        if (this.datetimeFormat != null) {
            builder.datetimeFormat(this.datetimeFormat);
        }

        return builder.build();
    }
}