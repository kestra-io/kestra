package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;


abstract class ShaBaseFilter implements Filter {
    private final String algorithm;

    ShaBaseFilter(String algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public List<String> getArgumentNames() {
        return null;
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self,
                        EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        if (input instanceof String str) {
            try {
                MessageDigest digest = MessageDigest.getInstance(algorithm);
                byte[]encodedHash = digest.digest((str).getBytes(StandardCharsets.UTF_8));
                return bytesToHex(encodedHash);
            } catch (Exception e) {
                throw new PebbleException(e, "Hashing exception encountered\n", lineNumber, self.getName());
            }
        } else {
            throw new PebbleException(null, "Need a string to hash\n", lineNumber, self.getName());
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte aByte : bytes) {
            String hex = Integer.toHexString(0xff & aByte);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

}
