package io.kestra.core.events;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

/**
 * Strongly-typed wrapper around a UUIDv7 identifier used for Kestra events.
 * <p>
 * UUIDv7 values are time-ordered, which allows lexicographic and unsigned
 * 128-bit comparison to reflect chronological ordering.
 */
public record EventId(@JsonValue UUID value) implements Comparable<EventId> {

    //  Generator that generates UUID using version 7 (Unix Epoch time+random based).
    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    public EventId {
        if (value == null) {
            throw new IllegalArgumentException("EventId UUID cannot be null");
        }
    }

    /**
     * Factory method for creating a new {@link EventId}.
     *
     * @return a new {@link EventId}.
     */
    public static EventId create() {
        return new EventId(GENERATOR.generate());
    }

    @JsonCreator
    public static EventId fromString(String str) {
        return new EventId(UUID.fromString(str));
    }

    /**
     * Compares two UUIDv7 values chronologically. UUIDv7 ordering corresponds
     * to treating the UUID as a 128-bit unsigned integer.
     *
     * @param other the other {@code EventId} to compare against
     * @return a negative value if this ID is older; zero if equal; positive if newer
     */
    @Override
    public int compareTo(EventId other) {
        int cmp = Long.compareUnsigned(this.value.getMostSignificantBits(), other.value.getMostSignificantBits());
        if (cmp != 0)
            return cmp;
        return Long.compareUnsigned(this.value.getLeastSignificantBits(), other.value.getLeastSignificantBits());
    }

    /**
     * Checks whether this ID is chronologically newer (greater) than the given ID.
     *
     * @param other the ID to compare against
     * @return {@code true} if this ID is newer; {@code false} otherwise
     */
    public boolean isNewerThan(final EventId other) {
        return this.compareTo(other) > 0;
    }

    /**
     * Checks whether this ID is chronologically older (less) than the given ID.
     *
     * @param other the ID to compare against
     * @return {@code true} if this ID is older; {@code false} otherwise
     */
    public boolean isOlderThan(final EventId other) {
        return this.compareTo(other) < 0;
    }

    /**
     * Returns the string representation of the underlying UUID.
     *
     * @return the UUID string
     */
    @Override
    public String toString() {
        return value.toString();
    }
}