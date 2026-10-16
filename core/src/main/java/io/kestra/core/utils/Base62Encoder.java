package io.kestra.core.utils;

import java.util.Objects;
import java.util.UUID;

/**
 * A utility class for encoding UUIDs in Base62 format.
 */
final class Base62Encoder {
    private static final short BASE62_UUID_LENGTH = 22;
    private static final long B62 = 62L;

    private static final char[] BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private Base62Encoder() {
    }

    /**
     * Creates a new Base62 encoded ID from a randomly generated UUID.
     *
     * @return a Base62 encoded string representation of a randomly generated UUID
     */
    public static String createId() {
        UUID uuid = UUID.randomUUID();
        return encode(uuid);
    }

    /**
     * Encodes a UUID into a Base62 string representation.
     *
     * @param uuid the UUID to encode
     * @return Base62 encoded string representation of the UUID
     */
    public static String encode(UUID uuid) {
        Objects.requireNonNull(uuid, "UUID cannot be null");

        char[] buffer = new char[BASE62_UUID_LENGTH];
        int bufferIndex = BASE62_UUID_LENGTH;

        long mostSigBits = uuid.getMostSignificantBits();
        long leastSigBits = uuid.getLeastSignificantBits();
        long[] limbs = { mostSigBits >>> Integer.SIZE, mostSigBits & (~(0L) >>> Integer.SIZE),
            leastSigBits >>> Integer.SIZE, leastSigBits & (~(0L) >>> Integer.SIZE) };

        while ((limbs[0] | limbs[1] | limbs[2] | limbs[3]) != 0) {
            long reminder = 0L;
            for (int i = 0; i < 4; ++i) {
                long current = (reminder << Integer.SIZE) + limbs[i];
                limbs[i] = current / B62;
                reminder = current % B62;
            }
            buffer[--bufferIndex] = BASE62_CHARS[(int) reminder];
        }

        return bufferIndex == BASE62_UUID_LENGTH ? "0" : new String(buffer, bufferIndex, BASE62_UUID_LENGTH - bufferIndex);
    }
}