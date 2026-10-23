package io.kestra.core.utils;

import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;
import java.util.UUID;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

@SuppressWarnings({ "deprecation" })
abstract public class IdUtils {
    private static final HashFunction HASH_FUNCTION = Hashing.md5();

    public static String create() {
        return FriendlyId.createFriendlyId();
    }

    public static String from(String from) {
        return FriendlyId.toFriendlyId(
            UUID.nameUUIDFromBytes(
                HASH_FUNCTION.hashString(from, StandardCharsets.UTF_8).asBytes()
            )
        );
    }

    /**
     * Produces a collision-safe identifier from the given parts.
     *
     * <p>Each non-null part is length-prefixed ({@code length:content}) so that
     * different logical part arrays always produce different output, regardless of
     * what characters appear inside individual parts.  For example,
     * {@code fromParts("team", "x_y")} and {@code fromParts("team_x", "y")}
     * produce distinct strings — a guarantee that a plain separator-based join
     * cannot provide when parts may contain the separator character.
     *
     * <p>Null parts are silently skipped, so
     * {@code fromParts(null, "a", "b")} equals {@code fromParts("a", "b")}.
     *
     * @param parts the parts to encode
     * @return a collision-safe identifier string
     */
    public static String fromParts(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String str : parts) {
            if (str != null) {
                sb.append(str.length()).append(':').append(str);
            }
        }
        return sb.toString();
    }

    public static String fromPartsAndSeparator(char separator, String... parts) {
        StringJoiner sj = new StringJoiner(String.valueOf(separator));
        for (String str : parts) {
            if (str != null) {
                sj.add(str);
            }
        }
        return sj.toString();
    }
}
