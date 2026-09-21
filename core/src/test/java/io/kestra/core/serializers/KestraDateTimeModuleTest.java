package io.kestra.core.serializers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the single on-the-wire date format shared by the queue, the JDBC {@code value} columns and gRPC.
 */
class KestraDateTimeModuleTest {

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private static final Instant INSTANT = Instant.parse("2024-06-01T10:00:00Z");

    record Holder(Instant instant, ZonedDateTime zonedDateTime) {}

    @Test
    void shouldWriteInstantWithSixFractionalDigitsInUtc() throws JsonProcessingException {
        // Given
        Holder holder = new Holder(Instant.parse("2024-06-01T10:00:00.123456789Z"), null);

        // When
        String json = MAPPER.writeValueAsString(holder);

        // Then — truncated to microseconds, never nine digits: MySQL's %f caps at six and returns
        // NULL beyond it, which is a NOT NULL violation on logs.timestamp
        assertThat(json).contains("\"instant\":\"2024-06-01T10:00:00.123456Z\"");
    }

    @Test
    void shouldWriteInstantFractionZeroPaddedAndNeverOmitted() throws JsonProcessingException {
        // Given a whole second, where ISO_INSTANT would drop the fractional block entirely
        Holder holder = new Holder(INSTANT, null);

        // When
        String json = MAPPER.writeValueAsString(holder);

        // Then
        assertThat(json).contains("\"instant\":\"2024-06-01T10:00:00.000000Z\"");
    }

    @Test
    void shouldReadEveryFractionalWidthWrittenByAnyVersion() throws JsonProcessingException {
        // Given the shapes that exist across upgrades: pre-0.21 3-digit, current 6-digit, plain
        // ISO_INSTANT with no fraction (what the queue used to carry), and nanosecond precision
        String[] stored = {
            "2024-06-01T10:00:00.000Z",
            "2024-06-01T10:00:00.000000Z",
            "2024-06-01T10:00:00Z",
            "2024-06-01T10:00:00.000000000Z",
        };

        // When / Then
        for (String value : stored) {
            assertThat(read(value)).as(value).isEqualTo(INSTANT);
        }
    }

    @Test
    void shouldReadInstantCarryingAnOffsetRatherThanZ() throws JsonProcessingException {
        // When / Then — same instant expressed in a non-UTC offset
        assertThat(read("2024-06-01T12:00:00.000+02:00")).isEqualTo(INSTANT);
        assertThat(read("2024-06-01T15:30:00.000+05:30")).isEqualTo(INSTANT);
    }

    @Test
    void shouldFallBackToJacksonForEpochNumbers() throws JsonProcessingException {
        // Given a numeric timestamp, which the fast path cannot parse
        String json = "{\"instant\":1717236000}";

        // When
        Holder holder = MAPPER.readValue(json, Holder.class);

        // Then the stock deserializer took over, so this stays a superset of the old behaviour
        assertThat(holder.instant()).isEqualTo(INSTANT);
    }

    @Test
    void shouldRejectAnImpossibleDateInsteadOfClampingIt() {
        // Given 2026 is not a leap year; the SMART resolver would silently return 2026-02-28
        assertThatThrownBy(() -> read("2026-02-29T00:00:00.000000Z"))
            .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void shouldRoundTripInstantUnchanged() throws JsonProcessingException {
        // Given
        Holder holder = new Holder(Instant.parse("2024-06-01T10:00:00.123456Z"), null);

        // When
        Holder result = MAPPER.readValue(MAPPER.writeValueAsString(holder), Holder.class);

        // Then microsecond precision survives a full round trip
        assertThat(result.instant()).isEqualTo(holder.instant());
    }

    @Test
    void shouldWriteZonedDateTimeWithSixFractionalDigits() throws JsonProcessingException {
        // Given — the writer renders in the mapper's timezone, so pin it instead of inheriting the
        // machine's: this assertion is about the fractional width, not about where the test runs
        ObjectMapper mapper = JacksonMapper.ofJson().copy().setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        Holder holder = new Holder(null, ZonedDateTime.parse("2024-06-01T12:00:00.123456789+02:00"));

        // When
        String json = mapper.writeValueAsString(holder);

        // Then — six digits, matching Instant, rather than the three it used to write
        assertThat(json).contains("2024-06-01T12:00:00.123456+02:00");
    }

    @Test
    void shouldReadLegacyThreeDigitZonedDateTime() throws JsonProcessingException {
        // Given the width the previous serializer wrote
        String json = "{\"zonedDateTime\":\"2024-06-01T12:00:00.000+02:00\"}";

        // When
        Holder holder = MAPPER.readValue(json, Holder.class);

        // Then
        assertThat(holder.zonedDateTime().toInstant()).isEqualTo(INSTANT);
    }

    @Test
    void shouldRenderZonedDateTimeInTheRequestedZoneWhenConverting() {
        // Given — JacksonMapper.toMap(value, zoneId) exists to render dates in a caller-chosen zone,
        // and ScheduleOnDates relies on it for its trigger variables
        Holder holder = new Holder(null, ZonedDateTime.parse("2024-06-01T10:00:00.000Z"));

        // When
        Map<String, Object> paris = JacksonMapper.toMap(holder, ZoneId.of("Europe/Paris"));
        Map<String, Object> kolkata = JacksonMapper.toMap(holder, ZoneId.of("Asia/Kolkata"));

        // Then the offset follows the requested zone instead of being pinned to the value's own
        assertThat(paris.get("zonedDateTime")).isEqualTo("2024-06-01T12:00:00.000000+02:00");
        assertThat(kolkata.get("zonedDateTime")).isEqualTo("2024-06-01T15:30:00.000000+05:30");
    }

    private Instant read(String value) throws JsonProcessingException {
        return MAPPER.readValue("{\"instant\":\"" + value + "\"}", Holder.class).instant();
    }
}
