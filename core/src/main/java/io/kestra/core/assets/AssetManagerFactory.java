package io.kestra.core.assets;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AssetEmit;
import io.kestra.core.runners.AssetEmitter;
import io.kestra.core.runners.RunContext;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

@Singleton
public class AssetManagerFactory {
    /**
     * @param context the task run emitting, or null outside a task run (a trigger)
     */
    public AssetEmitter of(RunContext runContext, @Nullable AssetEmitContext context, boolean enabled) {
        return new AssetEmitter() {
            @Override
            public void emit(AssetEmit assetEmit) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<AssetEmit> emitted() {
                return new ArrayList<>();
            }
        };
    }

    /**
     * Returns an emitter for the assets declared under a task's {@code assets}, which {@code enableAuto} does not gate.
     *
     * @param alreadyUsed the ids of the assets whose usage this task run already recorded, so they are only written and linked again
     */
    public AssetEmitter declared(RunContext runContext, AssetEmitContext context, State.Type state, Instant startDate, @Nullable Instant endDate, Set<String> alreadyUsed) {
        return new AssetEmitter() {
            @Override
            public void emit(AssetEmit assetEmit) {
            }

            @Override
            public List<AssetEmit> emitted() {
                return List.of();
            }
        };
    }

    /**
     * Ends the locks the task run took on its outputs, once an attempt ended in {@code state}.
     */
    public void finish(RunContext runContext, AssetEmitContext context, State.Type state) {
    }
}
