package io.kestra.plugin.core.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;

import io.micronaut.core.util.functional.ThrowingFunction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Schema(
    title = "Deduplicate a line-oriented file by key.",
    description = """
        Reads the file twice: first to map each key (from `expr`) to its last occurrence offset, then to write only those last occurrences to a new file. Avoids loading the full file in memory.

        Use for ordered “keep-last” semantics; expression can reference columns directly."""
)
@Plugin(
    examples = {
        @Example(
            title = "Remove duplicate customer emails from a CSV file.",
            full = true,
            code = """
                    id: deduplicate_items
                    namespace: company.team

                    tasks:
                      - id: generate_files
                        type: io.kestra.plugin.scripts.shell.Script
                        script: |
                          cat <<EOF > my_data.csv
                          order_id,customer_name,customer_email,product_id,price
                          1,Kelly Olsen,kelly@example.com,20,166.89
                          2,Miguel Moore,mccarthylee@example.net,14,171.63
                          3,Kelly Olsen,kelly@example.com,20,166.89
                          4,Jessica White,jessica@example.com,12,50.62
                          5,Jessica White,jessica@example.com,12,50.62
                          EOF
                        outputFiles:
                          - "my_data.csv"

                      - id: csv_to_ion
                        type: io.kestra.plugin.serdes.csv.CsvToIon
                        from: "{{ outputs.generate_files.outputFiles['my_data.csv'] }}"

                      - id: dedup
                        type: io.kestra.plugin.core.storage.DeduplicateItems
                        from: "{{ outputs.csv_to_ion.uri }}"
                        expr: "{{ customer_email }}"
                """
        )
    }
)
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class DeduplicateItems extends Task implements RunnableTask<DeduplicateItems.Output> {

    @Schema(
        title = "The file to be deduplicated"
    )
    @NotNull
    @PluginProperty(internalStorageURI = true)
    private Property<String> from;

    @Schema(
        title = "The Pebble expression to extract the deduplication key from each item",
        description = "Headers from the file can be referenced directly e.g. `{{ customer_email }}`"
    )
    @PluginProperty
    @NotNull
    private String expr;

    /**
     * {@inheritDoc}
     **/
    @Override
    public Output run(RunContext runContext) throws Exception {

        URI from = new URI(runContext.render(this.from).as(String.class).orElseThrow());

        final PebbleFieldExtractor keyExtractor = getKeyExtractor(runContext);

        // Read all records into memory (needed for two-pass dedup)
        var allRecords = new ArrayList<>();
        try (var input = new BufferedInputStream(runContext.storage().getFile(from), FileSerde.BUFFER_SIZE)) {
            FileSerde.read(input, allRecords::add);
        }

        final Map<String, Long> index = new HashMap<>();

        // 1st pass: build a map of key->index (keeps last occurrence)
        for (int i = 0; i < allRecords.size(); i++) {
            String textIon = PebbleFieldExtractor.MAPPER.writeValueAsString(allRecords.get(i));
            String key = keyExtractor.apply(textIon);
            index.put(key, (long) i);
        }

        // metrics
        long processedItemsTotal = 0L;
        long droppedItemsTotal = 0L;
        long numKeys = index.size();

        final Path path = runContext.workingDir().createTempFile(".ion");
        // 2nd pass: write deduplicated records
        try (final OutputStream output = new BufferedOutputStream(new FileOutputStream(path.toFile()), FileSerde.BUFFER_SIZE)) {
            for (int i = 0; i < allRecords.size(); i++) {
                Object record = allRecords.get(i);
                String textIon = PebbleFieldExtractor.MAPPER.writeValueAsString(record);
                String key = keyExtractor.apply(textIon);
                Long lastIndex = index.get(key);
                if (lastIndex != null && lastIndex == i) {
                    FileSerde.write(output, record);
                } else {
                    droppedItemsTotal++;
                }
                processedItemsTotal++;
            }
        }
        URI uri = runContext.storage().putFile(path.toFile());
        index.clear();
        return Output
            .builder()
            .uri(uri)
            .numKeys(numKeys)
            .processedItemsTotal(processedItemsTotal)
            .droppedItemsTotal(droppedItemsTotal)
            .build();
    }

    private PebbleFieldExtractor getKeyExtractor(RunContext runContext) {
        return new PebbleFieldExtractor(runContext, expr);
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The deduplicated file URI"
        )
        private final URI uri;

        @Schema(
            title = "The number of distinct keys observed by the task"
        )
        private final Long numKeys;

        @Schema(
            title = "The total number of items that was processed by the task"
        )
        private final Long processedItemsTotal;

        @Schema(
            title = "The total number of items that was dropped by the task"
        )
        private final Long droppedItemsTotal;
    }

    /**
     * Extracts a key from data using a 'pebble' expression.
     */
    private static class PebbleFieldExtractor implements ThrowingFunction<String, String, Exception> {

        protected static final ObjectMapper MAPPER = JacksonMapper.ofIon();
        private final RunContext runContext;
        private final String expression;

        /**
         * Creates a new {@link PebbleFieldExtractor} instance.
         *
         * @param expression the 'pebble' expression.
         */
        public PebbleFieldExtractor(final RunContext runContext,
            final String expression) {
            this.runContext = runContext;
            this.expression = expression;
        }

        /** {@inheritDoc} */
        @Override
        @SuppressWarnings("unchecked")
        public String apply(String data) throws Exception {
            try {
                return extract(MAPPER.readValue(data, Map.class));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        public String extract(final Map<String, Object> item) throws Exception {
            return runContext.render(expression, item);
        }
    }
}
