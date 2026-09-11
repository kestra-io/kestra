package io.kestra.core.utils;

import java.util.Objects;
import java.util.UUID;

public class Base62Encoder {
    private static final short BASE62_UUID_LENGTH = 22;
    private static final long B62 = 62L;

    private static final char[] BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

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

        // The encoding is done through a three-step long division process:
        // (1) Division of the mostSigBits by 62 and taking the remainder.
        // (2) Using the remainder from (1) and appending the high-order bits from leastSigBits
        // to make a new 64-bit number and dividing it by 62.
        // (3) Using the remainder from (2) and appending the remaining bits from leastSigBits
        // to yield the final remainder and the final quotient.

        char[] buffer = new char[BASE62_UUID_LENGTH];
        short bufferIndex = BASE62_UUID_LENGTH;

        long rem;
        long mostSigBits = uuid.getMostSignificantBits();
        long leastSigBits = uuid.getLeastSignificantBits();

        while (mostSigBits != 0 || leastSigBits != 0) {
            int leftShift;
            int rightShift = 0;
            long newLeastSigBits = 0L;

            // Stage (1)
            rem = Long.remainderUnsigned(mostSigBits, B62);
            mostSigBits = Long.divideUnsigned(mostSigBits, B62);

            // Stage (2)
            if (rem != 0) {
                // remainder < 62 which is at most 6 bits, so we can lshift rem by [58, 64) bits
                // and then OR it with most significant [58, 64) bits of leastSigBits to get a new 64-bit number
                leftShift = Long.numberOfLeadingZeros(rem);
                rem <<= leftShift;

                rightShift = Long.SIZE - leftShift;
                rem |= leastSigBits >>> rightShift;

                newLeastSigBits = Long.divideUnsigned(rem, B62);
                rem = Long.remainderUnsigned(rem, B62);
            }

            // Stage(3)
            // Last 0-6 bits of leastSigBits are processed with the remainder,
            // only if the remainder wasn't zero after the first division on mostSigBits,
            // otherwise it will process the whole leastSigBits
            int remainingBits = rightShift == 0 ? Long.SIZE : rightShift;
            int mask = ~0 >>> (Long.SIZE - remainingBits);
            leftShift = remainingBits;
            rem = (rem << leftShift) | (leastSigBits & mask);

            leastSigBits = Long.divideUnsigned(rem, B62) | (newLeastSigBits << remainingBits);
            rem = Long.remainderUnsigned(rem, B62);

            buffer[--bufferIndex] = BASE62_CHARS[(int) rem];
        }

        return bufferIndex == BASE62_UUID_LENGTH ? "0"
            : new String(buffer, bufferIndex, BASE62_UUID_LENGTH - bufferIndex);
    }
}