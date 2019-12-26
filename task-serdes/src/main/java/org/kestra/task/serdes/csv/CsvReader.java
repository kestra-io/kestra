    package org.kestra.task.serdes.csv;

import com.google.common.collect.ImmutableMap;
import de.siegmar.fastcsv.reader.CsvParser;
import de.siegmar.fastcsv.reader.CsvRow;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunOutput;
import org.kestra.core.serializers.ObjectsSerde;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class CsvReader extends Task implements RunnableTask {
    @NotNull
    private String from;

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
        URI from = new URI(runContext.render(this.from));
        de.siegmar.fastcsv.reader.CsvReader csvReader = this.csvReader();
        CsvParser csvParser = csvReader.parse(new InputStreamReader(runContext.uriToInputStream(from)));

        // temp file
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".javas");
        ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(tempFile));

        // convert
        Flowable<Object> flowable = Flowable
            .create(this.nextRow(csvParser), BackpressureStrategy.BUFFER)
            .map(r -> {
                if (header) {
                    return r.getFieldMap();
                } else {
                    return r.getFields();
                }
            })
            .observeOn(Schedulers.io())
            .doOnNext(row -> ObjectsSerde.write(output, row))
            .doOnComplete(() -> {
                output.close();
                csvParser.close();
            });

        // metrics & finalize
        Single<Long> count = flowable.count();
        Long lineCount = count.blockingGet();

        return RunOutput.builder()
            .outputs(ImmutableMap.of("uri", runContext.putFile(tempFile).getUri()))
            .build();
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

    private de.siegmar.fastcsv.reader.CsvReader csvReader() {
        de.siegmar.fastcsv.reader.CsvReader csvReader = new de.siegmar.fastcsv.reader.CsvReader();

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