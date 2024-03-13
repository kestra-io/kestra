package io.kestra.core.utils;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.base.Charsets;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings({"deprecation"})
abstract public class IdUtils {
    private static final HashFunction HASH_FUNCTION = Hashing.md5();
    private static final char ID_SEPARATOR = '_';

    public static String create() {
        return FriendlyId.createFriendlyId();
    }

    public static String from(String from) {
        return FriendlyId.toFriendlyId(
            UUID.nameUUIDFromBytes(
                HASH_FUNCTION.hashString(from, Charsets.UTF_8).asBytes()
            )
        );
    }

    public static String fromParts(String... parts) {
        return fromPartsAndSeparator(ID_SEPARATOR, parts);
    }

    public static String fromPartsAndSeparator(char separator, String... parts) {
        return Arrays.stream(parts)
            .filter(part -> part != null)
            .collect(Collectors.joining(String.valueOf(separator)));
    }
}
