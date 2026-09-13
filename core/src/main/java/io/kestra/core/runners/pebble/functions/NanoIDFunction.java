package io.kestra.core.runners.pebble.functions;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

public class NanoIDFunction implements KestraFunction {
    public static final String NAME = "nanoId";

    private static final int DEFAULT_LENGTH = 21;
    private static final char[] DEFAULT_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_".toCharArray();
    private static final SecureRandom secureRandom = new SecureRandom();

    private static final String LENGTH = "length";
    private static final String ALPHABET = "alphabet";

    private static final int MAX_LENGTH = 1000;
    private static final int MAX_ALPHABET_LENGTH = 256;

    @Override
    public Object execute(
        Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        int length = DEFAULT_LENGTH;
        if (args.containsKey(LENGTH) && (args.get(LENGTH) instanceof Long)) {
            length = parseLength(args, self, lineNumber);
        }
        char[] alphabet = DEFAULT_ALPHABET;
        if (args.containsKey(ALPHABET) && (args.get(ALPHABET) instanceof String)) {
            alphabet = ((String) args.get(ALPHABET)).toCharArray();
        }
        if (alphabet.length == 0) {
            throw new PebbleException(
                null,
                "The 'nanoId()' function field 'alphabet' must not be empty",
                lineNumber,
                self.getName()
            );
        }
        if (alphabet.length > MAX_ALPHABET_LENGTH) {
            throw new PebbleException(
                null,
                "The 'nanoId()' function field 'alphabet' must not contain more than: " + MAX_ALPHABET_LENGTH + " characters",
                lineNumber,
                self.getName()
            );
        }
        return createNanoID(length, alphabet);
    }

    private static int parseLength(Map<String, Object> args, PebbleTemplate self, int lineNumber) {
        var value = (Long) args.get(LENGTH);
        if (value < 1) {
            throw new PebbleException(
                null,
                "The 'nanoId()' function field 'length' must be greater than: 0",
                lineNumber,
                self.getName()
            );
        }
        if (value > MAX_LENGTH) {
            throw new PebbleException(
                null,
                "The 'nanoId()' function field 'length' must be lower than: " + MAX_LENGTH,
                lineNumber,
                self.getName()
            );
        }
        return Math.toIntExact(value);
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of(LENGTH, ALPHABET);
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        HashMap<String, String> defaults = new HashMap<>();
        defaults.put(LENGTH, null);
        defaults.put(ALPHABET, null);
        return defaults;
    }

    String createNanoID(int length, char[] alphabet) {
        final char[] data = new char[length];
        final byte[] bytes = new byte[length];

        // Power-of-two alphabets can use the fast '& mask' reduction, which is
        // uniform. For any other size, reject bytes that would introduce modulo
        // bias so every character keeps an equal chance of being drawn. The
        // caller bounds the alphabet to MAX_ALPHABET_LENGTH (256), so the cutoff
        // below is always in 1..255 and the rejection loop always terminates.
        if ((alphabet.length & (alphabet.length - 1)) == 0) {
            final int mask = alphabet.length - 1;
            secureRandom.nextBytes(bytes);
            for (int i = 0; i < length; ++i) {
                data[i] = alphabet[bytes[i] & mask];
            }
            return String.valueOf(data);
        }

        final int safeByteCutoff = 256 - (256 % alphabet.length);
        int position = bytes.length;
        for (int i = 0; i < length; ) {
            if (position == bytes.length) {
                secureRandom.nextBytes(bytes);
                position = 0;
            }
            final int value = bytes[position++] & 0xFF;
            if (value < safeByteCutoff) {
                data[i++] = alphabet[value % alphabet.length];
            }
        }
        return String.valueOf(data);
    }

}
