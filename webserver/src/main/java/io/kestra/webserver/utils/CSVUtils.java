package io.kestra.webserver.utils;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.exceptions.KestraRuntimeException;

import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.NamedCsvRecord;
import de.siegmar.fastcsv.reader.NamedCsvRecordHandler;
import de.siegmar.fastcsv.writer.CsvWriter;
import reactor.core.publisher.Flux;

/**
 * Renders records to RFC 4180 compliant CSV.
 *
 * <p>
 * The keys of the first record define the columns: every subsequent record is projected onto them, so a record
 * holding extra or missing keys can never shift the remaining values into the wrong columns.
 */
public final class CSVUtils {
    private CSVUtils() {
    }

    /**
     * Writes all records at once.
     *
     * @param outWriter the writer to render the CSV to
     * @param lines the records to render, nothing is written when empty
     */
    /**
     * Parses a CSV of a header row plus data rows into one map per row, keyed by header name.
     *
     * <p>
     * Uses the same FastCSV reader the write side uses, so a quoted field with an embedded comma or
     * newline round-trips. An empty or header-only CSV yields no rows; a malformed one is the
     * caller's mistake rather than a server fault.
     */
    public static List<Map<String, Object>> parseCSV(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        try (CsvReader<NamedCsvRecord> reader = CsvReader.builder().build(NamedCsvRecordHandler.of(), csv)) {
            for (NamedCsvRecord record : reader) {
                rows.add(new LinkedHashMap<>(record.getFieldsAsMap()));
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Could not parse the CSV: " + e.getMessage(), e);
        }

        return rows;
    }

    public static void toCSV(Writer outWriter, List<Map<String, Object>> lines) {

        try (var csvWriter = CsvWriter.builder().build(outWriter)) {
            if (lines.isEmpty()) {
                return;
            }
            List<String> headers = headers(lines.getFirst());
            csvWriter.writeRecord(headers);
            for (Map<String, Object> record : lines) {
                csvWriter.writeRecord(values(record, headers));
            }
        } catch (IOException e) {
            throw new KestraRuntimeException("could not convert to CSV", e);
        }
    }

    /**
     * Streams records as CSV, one emitted string per record, so that large exports are never held in memory.
     *
     * @param records the records to render
     * @return the CSV lines, empty when there is no record
     */
    public static Flux<String> toCSVFlux(Flux<Map<String, Object>> records) {
        return records.switchOnFirst((signal, flux) ->
        {
            if (!signal.hasValue()) {
                return Flux.empty();
            }
            List<String> headers = headers(signal.get());
            Flux<String> headerFlux = Flux.just(writeRecord(headers));
            Flux<String> rowsFlux = flux.map(record -> writeRecord(values(record, headers)));
            return headerFlux.concatWith(rowsFlux);
        });
    }

    private static List<String> headers(Map<String, Object> record) {
        return List.copyOf(record.keySet());
    }

    private static List<String> values(Map<String, Object> record, List<String> headers) {
        return headers.stream()
            .map(record::get)
            .map(value -> value != null ? value.toString() : "")
            .toList();
    }

    private static String writeRecord(List<String> values) {
        var stringWriter = new StringWriter();
        // buffering is disabled as each record is flushed downstream on its own
        try (var csvWriter = CsvWriter.builder().bufferSize(0).build(stringWriter)) {
            csvWriter.writeRecord(values);
        } catch (IOException e) {
            throw new KestraRuntimeException("could not convert to CSV", e);
        }
        return stringWriter.toString();
    }
}