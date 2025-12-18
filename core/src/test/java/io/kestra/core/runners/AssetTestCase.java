package io.kestra.core.runners;

import io.kestra.core.models.assets.*;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.queues.QueueException;
import io.kestra.core.services.AssetManagerFactory;
import io.kestra.core.services.AssetService;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class AssetTestCase {
    @Inject
    private AssetService mockedAssetService;
    @Inject
    private TestRunnerUtils testRunnerUtils;

    private static final List<Asset> capturedAsyncCreate = new CopyOnWriteArrayList<>();
    private static final List<Pair<AssetUser, Pair<List<AssetIdentifier>, List<AssetIdentifier>>>> capturedAssetLineage = new CopyOnWriteArrayList<>();
    private static final List<Asset> capturedEnabledDynamicAssets = new CopyOnWriteArrayList<>();
    private static final List<Asset> capturedDisabledDynamicAssets = new CopyOnWriteArrayList<>();

    public void staticAndDynamicAssets(String tenantId) throws QueueException, TimeoutException {
        Execution execution = testRunnerUtils.runOne(tenantId, "io.kestra.tests", "assets");

        Mockito.verify(mockedAssetService, Mockito.times(1)).run();

        // region assets-in-taskruns
        List<TaskRun> taskRuns = execution.getTaskRunList().stream().toList();
        assertThat(taskRuns).map(TaskRun::getAssets).map(AssetsInOut::getInputs).satisfiesExactlyInAnyOrder(
            assets -> assertThat(assets).isEmpty(),
            assets -> assertThat(assets).isEmpty(),
            assets -> assertThat(assets).satisfiesExactlyInAnyOrder(
                assetId -> assertThat(assetId.id()).isEqualTo("assets-flow-static-asset-non-existing-input-asset-uid")
            ),
            assets -> assertThat(assets).satisfiesExactlyInAnyOrder(
                assetId -> assertThat(assetId.id()).isEqualTo("assets-flow-static-asset-existing-input-uid")
            )
        );
        assertThat(taskRuns).map(TaskRun::getAssets).map(AssetsInOut::getOutputs).satisfiesExactlyInAnyOrder(
            assets -> assertThat(assets).satisfiesExactlyInAnyOrder(
                AssetTestCase::assertEnabledDynamicAsset,
                asset -> assertThat(asset.getId()).isEqualTo("assets-flow-static-emit-asset")
            ),
            assets -> assertThat(assets).isEmpty(),
            assets -> assertThat(assets).satisfiesExactlyInAnyOrder(
                asset -> assertThat(asset.getId()).isEqualTo("assets-flow-static-asset-non-existing-output-uid")
            ),
            assets -> assertThat(assets).satisfiesExactlyInAnyOrder(
                asset -> assertThat(asset.getId()).isEqualTo("assets-flow-static-asset-existing-output-uid")
            )
        );
        // endregion

        // region dynamic-assets
        assertThat(capturedEnabledDynamicAssets).anySatisfy(AssetTestCase::assertEnabledDynamicAsset);
        assertThat(capturedEnabledDynamicAssets).noneMatch(asset -> asset.getId().equals("assets-flow-emit-asset-auto-false-uid"));

        assertThat(capturedDisabledDynamicAssets).anySatisfy(AssetTestCase::assertDisabledDynamicAsset);
        assertThat(capturedDisabledDynamicAssets).noneMatch(asset -> asset.getId().equals("assets-flow-emit-asset-uid"));
        // endregion

        // region asset-creation
        assertThat(capturedAsyncCreate).satisfiesExactlyInAnyOrder(
            AssetTestCase::assertEnabledDynamicAsset,
            asset -> assertThat(asset.getId()).isEqualTo("assets-flow-static-emit-asset"),
            asset -> assertThat(asset.getId()).isEqualTo("assets-flow-static-asset-non-existing-output-uid"),
            asset -> assertThat(asset.getId()).isEqualTo("assets-flow-static-asset-existing-output-uid")
        );
        assertThat(capturedAsyncCreate).noneMatch(asset -> asset.getId().equals("assets-flow-emit-asset-auto-false-uid"));
        // endregion

        // region asset-lineage
        assertThat(capturedAssetLineage).satisfiesExactlyInAnyOrder(
            assetLineage -> {
                AssetUser assetUser = assetLineage.getLeft();
                assertAssetExecution(tenantId, assetUser, execution);
                assertThat(assetUser.taskRunId()).isEqualTo(execution.getTaskRunList().stream().filter(taskRun -> taskRun.getTaskId().equals("emit-asset"))
                    .findFirst().map(TaskRun::getId).orElseThrow());
                // No input assets
                assertThat(assetLineage.getRight().getLeft()).isEmpty();
                assertThat(assetLineage.getRight().getRight()).satisfiesExactlyInAnyOrder(
                    assetId -> assertThat(assetId.id()).isEqualTo("assets-flow-emit-asset-uid"),
                    assetId -> assertThat(assetId.id()).isEqualTo("assets-flow-static-emit-asset")
                );
            },
            // No lineage for the second taskrun due to `enableAuto: false`, below is for the third one
            assetLineage -> {
                AssetUser assetUser = assetLineage.getLeft();
                assertAssetExecution(tenantId, assetUser, execution);
                assertThat(assetUser.taskRunId()).isEqualTo(execution.getTaskRunList().stream().filter(taskRun -> taskRun.getTaskId().equals("static-asset-non-existing-input"))
                    .findFirst().map(TaskRun::getId).orElseThrow());
                assertThat(assetLineage.getRight().getLeft()).satisfiesExactlyInAnyOrder(
                    assetId -> assertThat(assetId.id()).isEqualTo("assets-flow-static-asset-non-existing-input-asset-uid")
                );
                assertThat(assetLineage.getRight().getRight()).satisfiesExactlyInAnyOrder(
                    assetId -> assertThat(assetId.id()).isEqualTo("assets-flow-static-asset-non-existing-output-uid")
                );
            },
            assetLineage -> {
                AssetUser assetUser = assetLineage.getLeft();
                assertAssetExecution(tenantId, assetUser, execution);
                assertThat(assetUser.taskRunId()).isEqualTo(execution.getTaskRunList().stream().filter(taskRun -> taskRun.getTaskId().equals("static-asset-existing-input"))
                    .findFirst().map(TaskRun::getId).orElseThrow());
                assertThat(assetLineage.getRight().getLeft()).satisfiesExactlyInAnyOrder(
                    assetId -> assertThat(assetId.id()).isEqualTo("assets-flow-static-asset-existing-input-uid")
                );
                assertThat(assetLineage.getRight().getRight()).satisfiesExactlyInAnyOrder(
                    assetId -> assertThat(assetId.id()).isEqualTo("assets-flow-static-asset-existing-output-uid")
                );
            }
        );
        // endregion
    }

    private static void assertAssetExecution(String tenantId, AssetUser assetUser, Execution execution) {
        assertThat(assetUser.tenantId()).isEqualTo(tenantId);
        assertThat(assetUser.namespace()).isEqualTo("io.kestra.tests");
        assertThat(assetUser.flowId()).isEqualTo("assets");
        assertThat(assetUser.flowRevision()).isEqualTo(execution.getFlowRevision());
        assertThat(assetUser.executionId()).isEqualTo(execution.getId());
    }

    private static void assertEnabledDynamicAsset(Asset asset) {
        assertThat(asset).isInstanceOf(TableAsset.class);
        TableAsset tableAsset = (TableAsset) asset;
        assertThat(tableAsset.getId()).isEqualTo("assets-flow-emit-asset-uid");
        assertThat(tableAsset.getType()).isEqualTo(TableAsset.ASSET_TYPE);
        assertThat(tableAsset.getDisplayName()).isEqualTo("My Table Asset");
        assertThat(tableAsset.getDescription()).isEqualTo("This is my table asset");
        assertThat(tableAsset.getSystem()).isEqualTo("MY_DB_SYSTEM");
        assertThat(tableAsset.getDatabase()).isEqualTo("my_database");
        assertThat(tableAsset.getSchema()).isEqualTo("my_schema");
        assertThat(tableAsset.getName()).isEqualTo("my_table");
        assertThat(tableAsset.getMetadata().get("owner")).isEqualTo("data-team");
    }

    private static void assertDisabledDynamicAsset(Asset asset) {
        assertThat(asset).isInstanceOf(TableAsset.class);
        TableAsset tableAsset = (TableAsset) asset;
        assertThat(tableAsset.getId()).isEqualTo("assets-flow-emit-asset-auto-false-uid");
        assertThat(tableAsset.getType()).isEqualTo(TableAsset.ASSET_TYPE);
        assertThat(tableAsset.getDisplayName()).isEqualTo("My Table Asset");
        assertThat(tableAsset.getDescription()).isEqualTo("This is my table asset");
        assertThat(tableAsset.getSystem()).isEqualTo("MY_DB_SYSTEM");
        assertThat(tableAsset.getDatabase()).isEqualTo("my_database");
        assertThat(tableAsset.getSchema()).isEqualTo("my_schema");
        assertThat(tableAsset.getName()).isEqualTo("my_table");
        assertThat(tableAsset.getMetadata().get("owner")).isEqualTo("data-team");
    }

    @Factory
    static class MockFactory {
        @Singleton
        @Replaces(AssetService.class)
        public AssetService mockedAssetService() {
            return Mockito.spy(new AssetService() {
                @Override
                public void asyncUpsert(AssetUser assetUser, Asset asset) {
                    capturedAsyncCreate.add(asset);
                }

                @Override
                public void assetLineage(AssetUser assetUser, List<AssetIdentifier> inputs, List<AssetIdentifier> outputs) {
                    capturedAssetLineage.add(Pair.of(assetUser, Pair.of(inputs, outputs)));
                }
            });
        }

        @Singleton
        @Replaces(AssetManagerFactory.class)
        public AssetManagerFactory mockedAssetManagerFactory() {
            return Mockito.spy(new AssetManagerFactory() {
                @Override
                public AssetEmitter of(boolean enabled) {
                    if (!enabled) {
                        return new AssetEmitter() {
                            @Override
                            public void upsert(Asset asset) {
                                capturedDisabledDynamicAssets.add(asset);
                            }

                            @Override
                            public List<Asset> outputs() {
                                return new ArrayList<>();
                            }
                        };
                    }

                    return new AssetEmitter() {
                        private final List<Asset> localCapturedAssets = new CopyOnWriteArrayList<>();

                        @Override
                        public void upsert(Asset asset) {
                            localCapturedAssets.add(asset);
                            capturedEnabledDynamicAssets.add(asset);
                        }

                        @Override
                        public List<Asset> outputs() {
                            return localCapturedAssets;
                        }
                    };
                }
            });
        }
    }
}
