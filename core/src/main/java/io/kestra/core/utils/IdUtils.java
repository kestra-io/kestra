package io.kestra.core.utils;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.base.Charsets;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.util.UUID;

@SuppressWarnings({"deprecation", "UnstableApiUsage"})
abstract public class IdUtils {
    private static final HashFunction HASH_FUNCTION = Hashing.md5();

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
}
