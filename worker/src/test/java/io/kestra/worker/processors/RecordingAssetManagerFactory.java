package io.kestra.worker.processors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.kestra.core.assets.AssetEmitContext;
import io.kestra.core.assets.AssetManagerFactory;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AssetEmit;
import io.kestra.core.runners.AssetEmitter;
import io.kestra.core.runners.RunContext;

class RecordingAssetManagerFactory extends AssetManagerFactory {
    record DeclaredEmit(String taskRunId, State.Type state, Set<String> alreadyUsed, AssetEmit emit, ExecutionKind executionKind) {}

    final List<DeclaredEmit> declaredEmits = new CopyOnWriteArrayList<>();
    final Map<String, State.Type> finished = new ConcurrentHashMap<>();
    volatile String refusal;
    volatile String lockRefusal;
    volatile boolean backendUnreachableAtFinish;

    List<DeclaredEmit> of(String taskRunId) {
        return declaredEmits.stream().filter(declaredEmit -> taskRunId.equals(declaredEmit.taskRunId())).toList();
    }

    @Override
    public AssetEmitter declared(RunContext runContext, AssetEmitContext context, State.Type state, Instant startDate, Instant endDate, Set<String> alreadyUsed) {
        List<String> failures = new ArrayList<>();
        List<String> lockRefusals = new ArrayList<>();
        return new AssetEmitter() {
            @Override
            public void emit(AssetEmit assetEmit) {
                declaredEmits.add(new DeclaredEmit(context.taskRunId(), state, alreadyUsed, assetEmit, context.executionKind()));
                if (refusal != null) {
                    failures.add(refusal);
                }
                if (lockRefusal != null && assetEmit.lock()) {
                    lockRefusals.add(lockRefusal);
                }
            }

            @Override
            public List<String> lockRefusals() {
                return lockRefusals;
            }

            @Override
            public List<AssetEmit> emitted() {
                return List.of();
            }

            @Override
            public List<String> failures() {
                return failures;
            }
        };
    }

    @Override
    public AssetEmitter of(RunContext runContext, AssetEmitContext context, boolean enabled) {
        List<AssetEmit> emits = new CopyOnWriteArrayList<>();
        return new AssetEmitter() {
            @Override
            public void emit(AssetEmit assetEmit) {
                if (enabled) {
                    emits.add(assetEmit);
                }
            }

            @Override
            public List<AssetEmit> emitted() {
                return emits;
            }
        };
    }

    @Override
    public void finish(RunContext runContext, AssetEmitContext context, State.Type state) {
        if (backendUnreachableAtFinish) {
            throw new IllegalStateException("The backend is unreachable.");
        }
        finished.put(context.taskRunId(), state);
    }
}
