package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

public class NanoIDFunction implements Function {

    private static final int DEFAULT_LENGTH = 21;
    private static final char[] DEFAULT_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_".toCharArray();
    private static final SecureRandom secureRandom = new SecureRandom();

    private static final String LENGTH = "length";
    private static final String ALPHABET = "alphabet";

    private static final int MAX_LENGTH = 1000;

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
        return createNanoID(length, alphabet);
    }

    private static int parseLength(Map<String, Object> args, PebbleTemplate self, int lineNumber) {
        var value = (Long) args.get(LENGTH);
        if(value > MAX_LENGTH) {
            throw new PebbleException(
                null,
                "The 'nanoId()' function field 'length' must be lower than: " + MAX_LENGTH,
                lineNumber,
                self.getName());
        }
        return Math.toIntExact(value);
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of(LENGTH,ALPHABET);
    }

    String createNanoID(int length, char[] alphabet){
        final char[] data = new char[length];
        final byte[] bytes = new byte[length];
        final int mask = alphabet.length-1;
        secureRandom.nextBytes(bytes);
        for (int i = 0; i < length; ++i) {
            data[i] = alphabet[bytes[i] & mask];
        }
        return String.valueOf(data);
    }


}
