package io.kestra.webserver.utils;

import io.kestra.core.exceptions.KestraRuntimeException;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

public class CSVUtils {
    public static void toCSV(Writer outWriter, List<Map<String, Object>> lines) {

        try (var csvWriter = de.siegmar.fastcsv.writer.CsvWriter.builder().build(outWriter)){
            if (lines.isEmpty()) {
                return;
            }
            csvWriter.writeRecord(lines.get(0).keySet().stream().map(Object::toString).toList());
            for (Map<String, Object> record : lines) {
                csvWriter.writeRecord(record.values().stream()
                    .map(value -> value != null ? value.toString() : "")
                    .map(Object::toString)
                    .toList());
            }
        } catch (IOException e) {
            throw new KestraRuntimeException("could not convert to CSV", e);
        }
    }

    public static Flux<String> toCSVFlux(Flux<Map<String, Object>> records) {
        return records.switchOnFirst((signal, flux) -> {
            if (!signal.hasValue()) {
                return Flux.empty();
            }
            Map<String, Object> first = signal.get();
            // Create the header from the keys of the first record
            String header = String.join(",", first.keySet()) + "\n";
            Flux<String> headerFlux = Flux.just(header);
            // Content of the CSV
            Flux<String> rowsFlux = flux.map(record ->
                record.values().stream()
                    .map(v -> v != null ? v.toString() : "")
                    .map(CSVUtils::escapeCsv)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("") + "\n"
            );
            return headerFlux.concatWith(rowsFlux);
        });
    }

    private static String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }
        return value;
    }
}