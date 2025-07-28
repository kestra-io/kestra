package io.kestra.webserver.utils;

import io.kestra.core.exceptions.KestraRuntimeException;

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
}