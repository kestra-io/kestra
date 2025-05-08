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

    @Override
    public Object execute(
        Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        int length = DEFAULT_LENGTH;
        if (args.containsKey("length") && (args.get("length") instanceof Integer)) {
            length = (int) args.get("length");
        }
        char[] alphabet = DEFAULT_ALPHABET;
        if (args.containsKey("alphabet") && (args.get("alphabet") instanceof String)) {
            alphabet = ((String) args.get("alphabet")).toCharArray();
        }
        return createNanoID(length, alphabet);
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of("length", "alphabet");
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
