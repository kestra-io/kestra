package io.kestra.plugin.core.storage;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class PurgeStorageTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private FlowRepositoryInterface flowRepository;

    private final List<Flow> createdFlows = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdFlows.forEach(flowRepository::delete);
        createdFlows.clear();
    }

    /** In-flight guard: files newer than endDate are preserved. */
    @Test
    void shouldPreserveExecutionFilesNewerThanEndDate() throws Exception {
        String namespace = uniqueNamespace();
        String flowId = IdUtils.create();
        createFlow(namespace, flowId);

        URI fileUri = putExecutionFile(namespace, flowId, "still running");
        assertThat(storageInterface.exists(MAIN_TENANT, namespace, fileUri)).isTrue();

        var output = purgeStorage(namespace, flowId, ZonedDateTime.now().minusMinutes(1)).run(runContext(flowId, namespace));

        assertThat(output.getScannedCount()).isEqualTo(1);
        assertThat(output.getPurgedCount()).isZero();
        assertThat(output.getDeletedFilesCount()).isZero();
        assertThat(storageInterface.exists(MAIN_TENANT, namespace, fileUri)).isTrue();
    }

    /**
     * Matching is per-file: within one execution, files older than endDate are deleted, newer files stay.
     * The user's endDate is the in-flight guard — this task targets orphans (no live executions), so the
     * trade-off of dropping the legacy "newest-file" subtree-level semantic is intentional.
     */
    @Test
    void shouldMatchPerFileWithinAnExecution() throws Exception {
        String namespace = uniqueNamespace();
        String flowId = IdUtils.create();
        createFlow(namespace, flowId);

        Execution execution = newExecution(namespace, flowId);
        URI oldFile = putFile(execution, "/tasks/task-a/run-1/old.txt", "old");
        long oldMtime = lastModified(namespace, oldFile);

        Thread.sleep(1_100);

        URI newFile = putFile(execution, "/tasks/task-b/run-1/new.txt", "new");
        long newMtime = lastModified(namespace, newFile);
        assertThat(newMtime).isGreaterThan(oldMtime);

        ZonedDateTime endDate = Instant.ofEpochMilli((oldMtime + newMtime) / 2).atZone(ZoneOffset.UTC);
        purgeStorage(namespace, flowId, endDate).run(runContext(flowId, namespace));

        assertThat(storageInterface.exists(MAIN_TENANT, namespace, oldFile)).isFalse();
        assertThat(storageInterface.exists(MAIN_TENANT, namespace, newFile)).isTrue();
    }

    @Test
    void shouldOnlyPurgeExecutionsWithinTheDateWindow() throws Exception {
        String namespace = uniqueNamespace();
        String flowId = IdUtils.create();
        createFlow(namespace, flowId);

        var files = putOldAndNewExecutionFiles(namespace, flowId);

        var output = purgeStorage(namespace, flowId, files.midPoint()).run(runContext(flowId, namespace));

        assertThat(output.getScannedCount()).isEqualTo(2);
        assertThat(output.getPurgedCount()).isEqualTo(1);
        assertThat(storageInterface.exists(MAIN_TENANT, namespace, files.oldFile())).isFalse();
        assertThat(storageInterface.exists(MAIN_TENANT, namespace, files.newFile())).isTrue();
    }

    @Test
    void shouldNotPurgeExecutionsOlderThanStartDate() throws Exception {
        String namespace = uniqueNamespace();
        String flowId = IdUtils.create();
        createFlow(namespace, flowId);

        var files = putOldAndNewExecutionFiles(namespace, flowId);

        var purge = PurgeStorage.builder()
            .namespace(Property.ofValue(namespace))
            .flowId(Property.ofValue(flowId))
            .dryRun(Property.ofValue(false))
            .startDate(Property.ofValue(files.midPoint().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .endDate(Property.ofValue(ZonedDateTime.now().plusMinutes(1).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();
        var output = purge.run(runContext(flowId, namespace));

        assertThat(output.getScannedCount()).isEqualTo(2);
        assertThat(output.getPurgedCount()).isEqualTo(1);
        assertThat(storageInterface.exists(MAIN_TENANT, namespace, files.oldFile())).isTrue();
        assertThat(storageInterface.exists(MAIN_TENANT, namespace, files.newFile())).isFalse();
    }

    @Test
    void shouldMatchButNotDeleteWhenDryRun() throws Exception {
        String namespace = uniqueNamespace();
        String flowId = IdUtils.create();
        createFlow(namespace, flowId);
        URI fileUri = putExecutionFile(namespace, flowId, "keep me");

        var output = purgeStorage(namespace, flowId, ZonedDateTime.now().plusMinutes(1), true).run(runContext(flowId, namespace));

        assertThat(output.getPurgedCount()).isEqualTo(1);
        assertThat(output.getDeletedFilesCount()).isZero();
        assertThat(storageInterface.exists(MAIN_TENANT, namespace, fileUri)).isTrue();
    }

    /** Regression for <a href="https://github.com/kestra-io/kestra-ee/issues/6699">kestra-ee#6699</a>: storage of deleted flows must be reachable from a namespace-only scan. */
    @Test
    void shouldFindOrphansOfDeletedFlowsWhenScopedByNamespaceOnly() throws Exception {
        String namespace = uniqueNamespace();
        String flowId = IdUtils.create();
        createFlow(namespace, flowId);
        URI fileUri = putExecutionFile(namespace, flowId, "orphan from deleted flow");
        flowRepository.delete(createdFlows.removeLast());

        var purge = PurgeStorage.builder()
            .namespace(Property.ofValue(namespace))
            .dryRun(Property.ofValue(false))
            .endDate(Property.ofValue(ZonedDateTime.now().plusMinutes(1).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();
        var output = purge.run(runContext(flowId, namespace));

        assertThat(output.getScannedCount()).isEqualTo(1);
        assertThat(output.getPurgedCount()).isEqualTo(1);
        assertThat(output.getDeletedFilesCount()).isGreaterThanOrEqualTo(1);
        assertThat(storageInterface.exists(MAIN_TENANT, namespace, fileUri)).isFalse();
    }

    /** Namespace-scoped purge is recursive: it reaches sub-namespaces (e.g. {@code a.b} also purges {@code a.b.child}). */
    @Test
    void shouldReachSubNamespacesWhenScopedByNamespace() throws Exception {
        String parentNamespace = uniqueNamespace();
        String childNamespace = parentNamespace + ".child";
        String parentFlowId = IdUtils.create();
        String childFlowId = IdUtils.create();
        createFlow(parentNamespace, parentFlowId);
        createFlow(childNamespace, childFlowId);
        URI parentFile = putExecutionFile(parentNamespace, parentFlowId, "in parent");
        URI childFile = putExecutionFile(childNamespace, childFlowId, "in child");

        var purge = PurgeStorage.builder()
            .namespace(Property.ofValue(parentNamespace))
            .dryRun(Property.ofValue(false))
            .endDate(Property.ofValue(ZonedDateTime.now().plusMinutes(1).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();
        var output = purge.run(runContext(parentFlowId, parentNamespace));

        assertThat(output.getScannedCount()).isEqualTo(2);
        assertThat(output.getPurgedCount()).isEqualTo(2);
        assertThat(storageInterface.exists(MAIN_TENANT, parentNamespace, parentFile)).isFalse();
        assertThat(storageInterface.exists(MAIN_TENANT, childNamespace, childFile)).isFalse();
    }

    /** Storage path /<ns>/executions/ is ambiguous between a flow named "executions" and a sub-namespace literally named "executions"; namespace-only scope skips it and requires explicit flowId. */
    @Test
    void shouldSkipAmbiguousExecutionsNameInNamespaceOnlyScan() throws Exception {
        String namespace = uniqueNamespace();
        String ambiguousFlowId = "executions";
        createFlow(namespace, ambiguousFlowId);
        URI fileUri = putExecutionFile(namespace, ambiguousFlowId, "may or may not be a flow");

        var namespaceScope = PurgeStorage.builder()
            .namespace(Property.ofValue(namespace))
            .dryRun(Property.ofValue(false))
            .endDate(Property.ofValue(ZonedDateTime.now().plusMinutes(1).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();
        var namespaceOutput = namespaceScope.run(runContext(ambiguousFlowId, namespace));

        assertThat(namespaceOutput.getScannedCount()).isZero();
        assertThat(namespaceOutput.getPurgedCount()).isZero();
        assertThat(storageInterface.exists(MAIN_TENANT, namespace, fileUri)).isTrue();

        // Explicit flowId still works.
        var explicitScope = PurgeStorage.builder()
            .namespace(Property.ofValue(namespace))
            .flowId(Property.ofValue(ambiguousFlowId))
            .dryRun(Property.ofValue(false))
            .endDate(Property.ofValue(ZonedDateTime.now().plusMinutes(1).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();
        var explicitOutput = explicitScope.run(runContext(ambiguousFlowId, namespace));

        assertThat(explicitOutput.getScannedCount()).isEqualTo(1);
        assertThat(explicitOutput.getPurgedCount()).isEqualTo(1);
        assertThat(storageInterface.exists(MAIN_TENANT, namespace, fileUri)).isFalse();
    }

    @Test
    void shouldRequireNamespaceWhenFlowIdIsSet() {
        var purge = PurgeStorage.builder()
            .flowId(Property.ofValue("some-flow"))
            .endDate(Property.ofValue(ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();

        assertThatThrownBy(() -> purge.run(runContext("some-flow", "some.namespace")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("namespace");
    }

    private void createFlow(String namespace, String flowId) {
        Flow flow = Flow.builder()
            .id(flowId)
            .namespace(namespace)
            .tenantId(MAIN_TENANT)
            .tasks(List.of(Return.builder().id("return").type(Return.class.getName()).format(Property.ofValue("ok")).build()))
            .build();
        flowRepository.create(GenericFlow.of(flow));
        createdFlows.add(flow);
    }

    private Execution newExecution(String namespace, String flowId) {
        return Execution.builder()
            .id(IdUtils.create())
            .namespace(namespace)
            .flowId(flowId)
            .tenantId(MAIN_TENANT)
            .state(new State().withState(State.Type.SUCCESS))
            .build();
    }

    /** Writes one file under a fresh execution at {@code executions/{id}/tasks/my-task/my-run/output.txt}. */
    private URI putExecutionFile(String namespace, String flowId, String content) throws Exception {
        return putFile(newExecution(namespace, flowId), "/tasks/my-task/my-run/output.txt", content);
    }

    /** Writes two files ~1.1s apart so old/new mtimes are distinct on second-granularity filesystems. */
    private OldNewFiles putOldAndNewExecutionFiles(String namespace, String flowId) throws Exception {
        URI oldFile = putExecutionFile(namespace, flowId, "old");
        long oldMtime = lastModified(namespace, oldFile);
        Thread.sleep(1_100);
        URI newFile = putExecutionFile(namespace, flowId, "new");
        long newMtime = lastModified(namespace, newFile);
        assertThat(newMtime).isGreaterThan(oldMtime);
        return new OldNewFiles(oldFile, oldMtime, newFile, newMtime);
    }

    private URI putFile(Execution execution, String relativePath, String content) throws Exception {
        URI fileUri = URI.create(StorageContext.forExecution(execution).getExecutionStorageURI() + relativePath);
        storageInterface.put(
            execution.getTenantId(),
            execution.getNamespace(),
            fileUri,
            new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
        );
        return fileUri;
    }

    private long lastModified(String namespace, URI fileUri) throws Exception {
        return storageInterface.getAttributes(MAIN_TENANT, namespace, fileUri).getLastModifiedTime();
    }

    private PurgeStorage purgeStorage(String namespace, String flowId, ZonedDateTime endDate) {
        return purgeStorage(namespace, flowId, endDate, false);
    }

    private PurgeStorage purgeStorage(String namespace, String flowId, ZonedDateTime endDate, boolean dryRun) {
        return PurgeStorage.builder()
            .namespace(Property.ofValue(namespace))
            .flowId(Property.ofValue(flowId))
            .dryRun(Property.ofValue(dryRun))
            .endDate(Property.ofValue(endDate.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();
    }

    private RunContext runContext(String flowId, String namespace) {
        return runContextFactory.of(flowId, namespace);
    }

    private static String uniqueNamespace() {
        // Namespace segments must start with a letter; the id may start with a digit.
        return "purgestorage.id" + IdUtils.create().toLowerCase();
    }

    private record OldNewFiles(URI oldFile, long oldMtime, URI newFile, long newMtime) {
        ZonedDateTime midPoint() {
            return Instant.ofEpochMilli((oldMtime + newMtime) / 2).atZone(ZoneOffset.UTC);
        }
    }
}
