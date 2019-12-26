package org.kestra.task.serdes.csv;

import com.github.jknack.handlebars.internal.lang3.ArrayUtils;
import com.google.common.collect.ImmutableMap;
import de.siegmar.fastcsv.writer.CsvAppender;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
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
import java.io.ObjectInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class CsvWriter extends Task implements RunnableTask {
    @NotNull
    private String from;

    @Builder.Default
    private Boolean header = true;

    @Builder.Default
    private Character fieldSeparator = ",".charAt(0);

    @Builder.Default
    private Character textDelimiter = "\"".charAt(0);

    @Builder.Default
    private Character[] lineDelimiter = ArrayUtils.toObject("\n".toCharArray());

    @Builder.Default
    private Boolean alwaysDelimitText = false;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        // temp file
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".csv");

        // writer
        de.siegmar.fastcsv.writer.CsvWriter csvWriter = this.csvWriter();
        CsvAppender csvAppender = csvWriter.append(tempFile, StandardCharsets.UTF_8);

        // reader
        URI from = new URI(runContext.render(this.from));
        ObjectInputStream inputStream = new ObjectInputStream(runContext.uriToInputStream(from));

        Flowable<Object> flowable = Flowable
            .create(ObjectsSerde.<String, String>reader(inputStream), BackpressureStrategy.BUFFER)
            .observeOn(Schedulers.io())
            .doOnNext(new Consumer<>() {
                private boolean first = false;

                @SuppressWarnings("unchecked")
                @Override
                public void accept(Object row) throws Exception {
                    if (row instanceof List) {
                        List<String> casted = (List<String>) row;

                        if (header) {
                            throw new IllegalArgumentException("Invalid data of type List with header");
                        }

                        for (final String value : casted) {
                            csvAppender.appendField(value);
                        }
                    } else if (row instanceof Map) {
                        Map<String, String> casted = (Map<String, String>) row;

                        if (!first) {
                            this.first = true;
                            if (header) {
                                for (final String value : casted.keySet()) {
                                    csvAppender.appendField(value);
                                }
                                csvAppender.endLine();
                            }
                        }

                        for (final String value : casted.values()) {
                            csvAppender.appendField(value);
                        }
                    }

                    csvAppender.endLine();
                }
            })
            .doOnComplete(() -> {
                csvAppender.close();
                inputStream.close();
            });


        // metrics & finalize
        Single<Long> count = flowable.count();
        Long lineCount = count.blockingGet();

        return RunOutput.builder()
            .outputs(ImmutableMap.of("uri", runContext.putFile(tempFile).getUri()))
            .build();
    }

    private de.siegmar.fastcsv.writer.CsvWriter csvWriter() {
        de.siegmar.fastcsv.writer.CsvWriter csvWriter = new de.siegmar.fastcsv.writer.CsvWriter();

        csvWriter.setTextDelimiter(this.textDelimiter);
        csvWriter.setFieldSeparator(this.fieldSeparator);
        csvWriter.setLineDelimiter(ArrayUtils.toPrimitive(this.lineDelimiter));
        csvWriter.setAlwaysDelimitText(this.alwaysDelimitText);

        return csvWriter;
    }
}