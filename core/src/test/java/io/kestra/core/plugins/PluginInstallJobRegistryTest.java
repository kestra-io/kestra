package io.kestra.core.plugins;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.transfer.TransferListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.docs.JsonSchemaCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class PluginInstallJobRegistryTest {

    private PluginManager pluginManager;
    private JsonSchemaCache jsonSchemaCache;
    private PluginInstallJobRegistry registry;

    @BeforeEach
    void setUp() {
        pluginManager = mock(PluginManager.class);
        jsonSchemaCache = mock(JsonSchemaCache.class);
        registry = new PluginInstallJobRegistry(pluginManager, jsonSchemaCache, 2);
    }

    @AfterEach
    void tearDown() {
        registry.shutdown();
    }

    // ─── submit ───────────────────────────────────────────────────────────────

    @Test
    void shouldReturnPendingJobImmediatelyAfterSubmit() throws Exception {
        // Given — block the install until the job has been asserted, otherwise the worker
        // thread may already have flipped the job to SUCCEEDED before get() runs
        List<PluginArtifact> artifacts = List.of(artifact("io.kestra.plugin", "plugin-scripts", "1.0.0"));
        CountDownLatch releaseInstall = new CountDownLatch(1);

        when(pluginManager.install(anyList(), anyList(), anyBoolean(), isNull(), any(TransferListener.class)))
            .thenAnswer(invocation ->
            {
                releaseInstall.await(5, TimeUnit.SECONDS);
                return artifacts;
            });

        // When
        UUID jobId = registry.submit(artifacts);

        // Then
        Optional<PluginInstallJob> job = registry.get(jobId);
        assertThat(job).isPresent();
        assertThat(job.get().id()).isEqualTo(jobId);
        assertThat(job.get().status()).isIn(PluginInstallJob.Status.PENDING, PluginInstallJob.Status.RUNNING);
        assertThat(job.get().artifacts()).containsExactlyElementsOf(artifacts);

        releaseInstall.countDown();
        awaitTerminal(jobId, Duration.ofSeconds(5));
    }

    @Test
    void shouldReturnEmptyForUnknownJobId() {
        // When / Then
        assertThat(registry.get(UUID.randomUUID())).isEmpty();
    }

    // ─── successful install ───────────────────────────────────────────────────

    @Test
    void shouldTransitionToSucceededAfterInstall() throws Exception {
        // Given
        List<PluginArtifact> artifacts = List.of(artifact("io.kestra.plugin", "plugin-scripts", "1.0.0"));
        CountDownLatch latch = new CountDownLatch(1);

        when(pluginManager.install(anyList(), anyList(), anyBoolean(), isNull(), any(TransferListener.class)))
            .thenAnswer(invocation ->
            {
                latch.countDown();
                return artifacts;
            });

        // When
        UUID jobId = registry.submit(artifacts);

        // Then — wait for the worker to complete
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        awaitTerminal(jobId, Duration.ofSeconds(5));

        PluginInstallJob job = registry.get(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo(PluginInstallJob.Status.SUCCEEDED);
        assertThat(job.finishedAt()).isNotNull();
        assertThat(job.error()).isNull();
        verify(jsonSchemaCache).clear();
    }

    // ─── failed install ───────────────────────────────────────────────────────

    @Test
    void shouldTransitionToFailedOnException() throws Exception {
        // Given
        List<PluginArtifact> artifacts = List.of(artifact("io.kestra.plugin", "plugin-scripts", "1.0.0"));
        String errorMessage = "network error";

        when(pluginManager.install(anyList(), anyList(), anyBoolean(), isNull(), any(TransferListener.class)))
            .thenThrow(new RuntimeException(errorMessage));

        // When
        UUID jobId = registry.submit(artifacts);

        // Then
        awaitTerminal(jobId, Duration.ofSeconds(5));

        PluginInstallJob job = registry.get(jobId).orElseThrow();
        assertThat(job.status()).isEqualTo(PluginInstallJob.Status.FAILED);
        assertThat(job.error()).isEqualTo(errorMessage);
        assertThat(job.finishedAt()).isNotNull();
        verify(jsonSchemaCache, never()).clear();
    }

    // ─── TransferListener progress ────────────────────────────────────────────

    @Test
    void shouldUpdateProgressMapViaTransferListener() throws Exception {
        // Given
        List<PluginArtifact> artifacts = List.of(artifact("io.kestra.plugin", "plugin-scripts", "1.0.0"));
        CountDownLatch latch = new CountDownLatch(1);

        when(pluginManager.install(anyList(), anyList(), anyBoolean(), isNull(), any(TransferListener.class)))
            .thenAnswer(invocation ->
            {
                TransferListener listener = invocation.getArgument(4);
                // Simulate a transfer event via the listener's progress map
                // by writing directly to the job's progress map through the listener
                listener.transferProgressed(mockTransferEvent("http://repo/plugin.jar", 512, 1024));
                latch.countDown();
                return artifacts;
            });

        // When
        UUID jobId = registry.submit(artifacts);
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        awaitTerminal(jobId, Duration.ofSeconds(5));

        // Then
        PluginInstallJob job = registry.get(jobId).orElseThrow();
        // After transferProgressed, the entry should be PROGRESSING
        assertThat(job.progress()).hasEntrySatisfying(
            "http://repo/plugin.jar",
            p ->
            {
                assertThat(p.transferred()).isEqualTo(512L);
                assertThat(p.total()).isEqualTo(1024L);
            }
        );
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private PluginArtifact artifact(String groupId, String artifactId, String version) {
        return new PluginArtifact(groupId, artifactId, "jar", null, version, null);
    }

    private void awaitTerminal(UUID jobId, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            Optional<PluginInstallJob> job = registry.get(jobId);
            if (job.isPresent() && job.get().isTerminal())
                return;
            Thread.sleep(50);
        }
        throw new AssertionError("Job " + jobId + " did not reach terminal state within " + timeout);
    }

    private org.eclipse.aether.transfer.TransferEvent mockTransferEvent(String url, long transferred, long total) {
        var resource = mock(org.eclipse.aether.transfer.TransferResource.class);
        when(resource.getRepositoryUrl()).thenReturn(url);
        when(resource.getResourceName()).thenReturn("");
        when(resource.getContentLength()).thenReturn(total);

        var event = mock(org.eclipse.aether.transfer.TransferEvent.class);
        when(event.getResource()).thenReturn(resource);
        when(event.getTransferredBytes()).thenReturn(transferred);

        return event;
    }
}
