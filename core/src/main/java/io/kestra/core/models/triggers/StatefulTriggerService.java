package io.kestra.core.models.triggers;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVValueAndMetadata;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class StatefulTriggerService {
    public record Entry(String uri, String version, Instant modifiedAt, Instant lastSeenAt) {
        public static Entry candidate(String uri, String version, Instant modifiedAt) {
            return new Entry(uri, version, modifiedAt, null);
        }
    }

    public record StateUpdate(boolean fire, boolean isNew) {}

    public static Map<String, Entry> readState(RunContext runContext, String key, Optional<Duration> ttl) {
        try {
            var kv = runContext.namespaceKv(runContext.flowInfo().namespace()).getValue(key);
            if (kv.isEmpty()) {
                return new HashMap<>();
            }

            var entries = JacksonMapper.ofJson().readValue((byte[]) kv.get().value(), new TypeReference<List<Entry>>() {});

            var cutoff = ttl.map(d -> Instant.now().minus(d)).orElse(Instant.MIN);

            return entries.stream()
                .filter(e -> Optional.ofNullable(e.lastSeenAt()).orElse(Instant.now()).isAfter(cutoff))
                .collect(Collectors.toMap(Entry::uri, e -> e));
        } catch (Exception e) {
            runContext.logger().warn("readState failed", e);
            return new HashMap<>();
        }
    }

    public static void writeState(RunContext runContext, String key, Map<String, Entry> state, Optional<Duration> ttl) {
        try {
            var bytes = JacksonMapper.ofJson().writeValueAsBytes(state.values());
            var meta = new KVMetadata("trigger state", ttl.orElse(null));

            runContext.namespaceKv(runContext.flowInfo().namespace()).put(key, new KVValueAndMetadata(meta, bytes));
        } catch (Exception e) {
            runContext.logger().warn("writeState failed", e);
        }
    }

    public static StateUpdate computeAndUpdateState(Map<String, Entry> state, Entry candidate, StatefulTriggerInterface.On on) {
        var prev = state.get(candidate.uri());
        var isNew = prev == null;
        var fire = shouldFire(prev, candidate.version(), on);

        Instant lastSeenAt;

        if (fire || isNew) {
            // it is new seen or changed
            lastSeenAt = Instant.now();
        } else if (prev.lastSeenAt() != null) {
            // it is unchanged but already tracked before
            lastSeenAt = prev.lastSeenAt();
        } else {
            lastSeenAt = Instant.now();
        }

        var newEntry = new Entry(candidate.uri(), candidate.version(), candidate.modifiedAt(), lastSeenAt);

        state.put(candidate.uri(), newEntry);

        return new StatefulTriggerService.StateUpdate(fire, isNew);
    }

    public static boolean shouldFire(Entry prev, String version, StatefulTriggerInterface.On on) {
        if (prev == null) {
            return on == StatefulTriggerInterface.On.CREATE || on == StatefulTriggerInterface.On.CREATE_OR_UPDATE;
        }
        if (!Objects.equals(prev.version(), version)) {
            return on == StatefulTriggerInterface.On.UPDATE || on == StatefulTriggerInterface.On.CREATE_OR_UPDATE;
        }
        return false;
    }

    public static String defaultKey(String ns, String flowId, String triggerId) {
        return String.join("_", ns, flowId, triggerId);
    }
}
