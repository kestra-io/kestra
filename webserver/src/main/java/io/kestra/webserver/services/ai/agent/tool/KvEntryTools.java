package io.kestra.webserver.services.ai.agent.tool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.format.DateTimeParseException;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.models.kv.KVType;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValue;

import io.micronaut.core.annotation.Nullable;

/**
 * Shared steps of the KV tools: the lookups that fail with a message the model can act on, and the unchecked wrapping
 * of the store's I/O. Every failure is an {@link IllegalArgumentException} naming the key and the namespace, so the
 * tool dispatch turns it into a tool error the model can read rather than a failed turn.
 */
final class KvEntryTools {
    private KvEntryTools() {
    }

    /** A {@link KVStore} call, which declares {@link IOException} while a {@code @Tool} method cannot. */
    interface StoreCall<T> {
        T call() throws IOException;
    }

    /**
     * Runs a store call, rethrowing its {@link IOException} as an {@link UncheckedIOException}.
     *
     * @return the call's result, {@code null} for a call that only has a side effect
     */
    static <T> T io(final StoreCall<T> call) {
        try {
            return call.call();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The entry's metadata, for an entry that exists and is not deleted.
     *
     * @throws IllegalArgumentException when there is no such entry
     */
    static KVEntry entryOrThrow(final KVStore store, final String namespace, final String key) {
        return io(() -> store.get(key)).orElseThrow(() -> notFound(namespace, key));
    }

    /**
     * The entry's value in its stored type, with a binary value turned into a string as the KV API does.
     *
     * @throws IllegalArgumentException when there is no such entry, or when it has expired (a distinct message)
     */
    static Object valueOrThrow(final KVStore store, final String namespace, final String key) {
        try {
            Object value = store.getValue(key).map(KVValue::value).orElseThrow(() -> notFound(namespace, key));
            return value instanceof byte[] bytes ? new String(bytes) : value;
        } catch (ResourceExpiredException e) {
            throw new IllegalArgumentException("KV entry '%s' in namespace '%s' has expired.".formatted(key, namespace));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The type the KV API reports for the entry, read back from the store so a value parsed from text is typed the
     * way it was stored rather than the way it was written.
     *
     * @throws IllegalArgumentException when there is no such entry or it has expired
     */
    static KVType typeOf(final KVStore store, final String namespace, final String key) {
        return KVType.from(valueOrThrow(store, namespace, key));
    }

    /**
     * Parses a tool's {@code ttl} argument.
     *
     * @return the duration, or {@code null} when the argument was omitted or blank
     * @throws IllegalArgumentException when the text is not an ISO-8601 duration
     */
    static Duration ttl(@Nullable final String ttl) {
        if (ttl == null || ttl.isBlank()) {
            return null;
        }
        Duration duration;
        try {
            duration = Duration.parse(ttl);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("ttl '%s' is not an ISO-8601 duration (e.g. PT1H or P7D).".formatted(ttl));
        }
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("ttl '%s' must be greater than zero; omit it for an entry that does not expire.".formatted(ttl));
        }
        return duration;
    }

    /** The exception every lookup of a missing entry throws, so the tools report it with one message. */
    static IllegalArgumentException notFound(final String namespace, final String key) {
        return new IllegalArgumentException("KV entry '%s' not found in namespace '%s'.".formatted(key, namespace));
    }
}
