package io.kestra.core.serializers;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;

/**
 * Gives {@link Instant} and {@link ZonedDateTime} one on-the-wire representation across every JSON
 * surface — the queue, the JDBC {@code value} columns and gRPC — so a value cannot change shape by
 * crossing between them.
 *
 * <p>
 * Both are written with a <strong>fixed six-digit</strong> fractional second. The width is
 * load-bearing, not cosmetic: the H2 and MySQL generated date columns parse these strings with
 * fixed-offset SQL expressions, and MySQL's {@code %f} caps at microseconds — a nine-digit fraction
 * makes {@code STR_TO_DATE} return {@code NULL}. Six digits is therefore both the floor
 * and the ceiling.
 *
 * <p>
 * Reading is deliberately lenient, because the columns and queue hold a mix of shapes written by older versions.
 *
 * <p>
 * These serializers are intentionally <em>not</em> contextual, so a per-property
 * {@code @JsonFormat} on an {@code Instant} has no effect: the width is a contract with the database schema.
 */
public final class KestraDateTimeModule extends SimpleModule {

    /** Always UTC with a trailing {@code Z}; six fractional digits, zero-padded, never omitted. */
    private static final DateTimeFormatter INSTANT_WRITER =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

    /** Six fractional digits plus the offset; {@code XXX} renders a zero offset as {@code Z}. */
    private static final DateTimeFormatter ZONED_DATE_TIME_WRITER =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSXXX", Locale.ROOT);

    /**
     * Accepts zero to nine fractional digits and either {@code Z} or a numeric offset.
     * {@code uuuu} rather than {@code yyyy} so {@link ResolverStyle#STRICT} can resolve without an
     * era.
     */
    private static final DateTimeFormatter INSTANT_READER = new DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .appendPattern("XXX")
        .toFormatter(Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT);

    public KestraDateTimeModule() {
        this.addSerializer(Instant.class, new InstantSerializer());
        this.addDeserializer(Instant.class, new LenientInstantDeserializer());
        this.addSerializer(ZonedDateTime.class, new ZonedDateTimeSerializer());
        // ZonedDateTime keeps Jackson's deserializer: it already accepts any fractional width.
    }

    private static class InstantSerializer extends JsonSerializer<Instant> {
        @Override
        public void serialize(Instant value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            char[] formatted = formatInstantFast(value);
            if (formatted != null) {
                generator.writeString(formatted, 0, formatted.length);
            } else {
                generator.writeString(INSTANT_WRITER.format(value));
            }
        }
    }

    private static char[] formatInstantFast(Instant value) {
        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(value.getEpochSecond(), value.getNano(), ZoneOffset.UTC);
        if (dateTime.getYear() < 0 || dateTime.getYear() > 9999) {
            return null;
        }

        char[] out = new char[27];
        put(out, 0, dateTime.getYear(), 4);
        out[4] = '-';
        put(out, 5, dateTime.getMonthValue(), 2);
        out[7] = '-';
        put(out, 8, dateTime.getDayOfMonth(), 2);
        out[10] = 'T';
        put(out, 11, dateTime.getHour(), 2);
        out[13] = ':';
        put(out, 14, dateTime.getMinute(), 2);
        out[16] = ':';
        put(out, 17, dateTime.getSecond(), 2);
        out[19] = '.';
        put(out, 20, dateTime.getNano() / 1_000, 6);
        out[26] = 'Z';
        return out;
    }

    private static void put(char[] out, int offset, int value, int width) {
        for (int i = offset + width - 1; i >= offset; i--) {
            out[i] = (char) ('0' + value % 10);
            value /= 10;
        }
    }

    /**
     * Parses {@code uuuu-MM-ddTHH:mm:ss[.f{1,9}]Z} without allocating, or returns {@code null} for any
     * other shape or an out-of-range field so that the caller can defer to the strict formatter.
     */
    private static Instant parseInstantFast(char[] text, int offset, int length) {
        if (length < 20 || text[offset + length - 1] != 'Z' || text[offset + 4] != '-' || text[offset + 7] != '-'
            || text[offset + 10] != 'T' || text[offset + 13] != ':' || text[offset + 16] != ':') {
            return null;
        }

        int year = digits(text, offset, 4);
        int month = digits(text, offset + 5, 2);
        int day = digits(text, offset + 8, 2);
        int hour = digits(text, offset + 11, 2);
        int minute = digits(text, offset + 14, 2);
        int second = digits(text, offset + 17, 2);
        if (year < 0 || month < 0 || day < 0 || hour < 0 || minute < 0 || second < 0) {
            return null;
        }

        int nano = 0;
        if (length > 20) {
            int fractionDigits = length - 21;
            if (text[offset + 19] != '.' || fractionDigits < 1 || fractionDigits > 9) {
                return null;
            }
            int fraction = digits(text, offset + 20, fractionDigits);
            if (fraction < 0) {
                return null;
            }
            nano = fraction;
            for (int i = fractionDigits; i < 9; i++) {
                nano *= 10;
            }
        }

        try {
            return LocalDateTime.of(year, month, day, hour, minute, second, nano).toInstant(ZoneOffset.UTC);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static int digits(char[] text, int offset, int count) {
        int result = 0;
        for (int i = offset; i < offset + count; i++) {
            int digit = text[i] - '0';
            if (digit < 0 || digit > 9) {
                return -1;
            }
            result = result * 10 + digit;
        }
        return result;
    }

    /**
     * Honors the context timezone the way Jackson's own {@code ZonedDateTimeSerializer} does, so
     * {@link JacksonMapper#toMap(Object, java.time.ZoneId)} keeps rendering in the requested zone —
     * {@code ScheduleOnDates} depends on it for its trigger variables.
     */
    private static class ZonedDateTimeSerializer extends JsonSerializer<ZonedDateTime> {
        @Override
        public void serialize(ZonedDateTime value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            boolean useContextTimeZone = provider.getConfig().hasExplicitTimeZone()
                && provider.isEnabled(SerializationFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE);

            ZonedDateTime adjusted = useContextTimeZone
                ? value.withZoneSameInstant(provider.getConfig().getTimeZone().toZoneId())
                : value;

            generator.writeString(ZONED_DATE_TIME_WRITER.format(adjusted));
        }
    }

    private static class LenientInstantDeserializer extends JsonDeserializer<Instant> {
        @Override
        public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() == JsonToken.VALUE_STRING) {
                try {
                    // fast-path: reduce allocation with respect to the default more lenient Jackson InstantDeserializer
                    char[] chars = parser.getTextCharacters();
                    int offset = parser.getTextOffset();
                    int length = parser.getTextLength();
                    Instant fast = parseInstantFast(chars, offset, length);
                    return fast != null ? fast : INSTANT_READER.parse(new String(chars, offset, length), Instant::from);
                } catch (DateTimeException | ArithmeticException ignored) {
                    // Not one of our shapes; Jackson accepts forms the fast path does not.
                }
            }

            return InstantDeserializer.INSTANT.deserialize(parser, context);
        }
    }
}
