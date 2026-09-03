package io.kestra.core.assets;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.AssetIdentifier;
import io.kestra.core.models.assets.AssetUser;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.queues.QueueException;

import io.micronaut.context.annotation.Secondary;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

public interface AssetService {

    void asyncUpsert(AssetUser assetUser, Asset asset) throws QueueException, InternalException;

    Asset syncUpsert(@Nullable Asset inRepository, AssetUser assetUser, Asset assetToUpsert) throws QueueException;

    void assetLineage(AssetUser assetUser, List<AssetIdentifier> inputs, List<AssetIdentifier> outputs) throws QueueException;

    void deleteAsset(Asset toDelete, AssetUser assetUser) throws QueueException;

    /**
     * Emits lineage for, then upserts (mark-and-continue: one asset's conflict must not stop the rest),
     * a just-terminated taskRun's declared assets. If any upsert fails, escalates the taskRun's state
     * per the owning task's {@code assetFailureBehavior}, clamped by {@code allowFailure}/{@code allowWarning}.
     * The owning task is only resolved from {@code flow} once an upsert has actually failed.
     *
     * @return the escalated taskRun, or empty if its state is unchanged
     */
    Optional<TaskRun> processTaskRunAssets(AssetUser assetUser, TaskRun taskRun, Execution execution, Supplier<FlowWithSource> flow, ExecutionKind executionKind);

    @Singleton
    @Secondary
    class NoopAssetService implements AssetService {
        @Override
        public void asyncUpsert(AssetUser assetUser, Asset asset) throws QueueException, InternalException {
            // no-op
        }

        @Override
        public Asset syncUpsert(@Nullable Asset inRepository, AssetUser assetUser, Asset assetToUpsert) throws QueueException {
            // no-op
            return null;
        }

        @Override
        public void assetLineage(AssetUser assetUser, List<AssetIdentifier> inputs, List<AssetIdentifier> outputs) {
            // no-op
        }

        @Override
        public void deleteAsset(Asset toDelete, AssetUser assetUser) throws QueueException {
            // no-op
        }

        @Override
        public Optional<TaskRun> processTaskRunAssets(AssetUser assetUser, TaskRun taskRun, Execution execution, Supplier<FlowWithSource> flow, ExecutionKind executionKind) {
            // no-op
            return Optional.empty();
        }
    }
}
