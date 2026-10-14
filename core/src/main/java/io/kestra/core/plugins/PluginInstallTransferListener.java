package io.kestra.core.plugins;

import java.util.Map;

import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;

import lombok.RequiredArgsConstructor;

/**
 * An Aether {@link org.eclipse.aether.transfer.TransferListener} that writes per-artifact byte
 * progress into the {@link PluginInstallJob#progress()} map of a running install job.
 * <p>
 * A new instance must be created for each job, bound to that job's shared {@code progress} map.
 */
@RequiredArgsConstructor
public class PluginInstallTransferListener extends AbstractTransferListener {

    /** The live progress map of the owning {@link PluginInstallJob}. */
    private final Map<String, PluginInstallJob.ArtifactProgress> progress;

    @Override
    public void transferStarted(TransferEvent event) throws TransferCancelledException {
        String resource = resource(event);
        long total = event.getResource().getContentLength();
        progress.put(resource, new PluginInstallJob.ArtifactProgress(resource, 0, total, PluginInstallJob.ArtifactState.STARTED));
    }

    @Override
    public void transferProgressed(TransferEvent event) throws TransferCancelledException {
        String resource = resource(event);
        long transferred = event.getTransferredBytes();
        long total = event.getResource().getContentLength();
        progress.put(resource, new PluginInstallJob.ArtifactProgress(resource, transferred, total, PluginInstallJob.ArtifactState.PROGRESSING));
    }

    @Override
    public void transferSucceeded(TransferEvent event) {
        String resource = resource(event);
        long total = event.getTransferredBytes();
        progress.put(resource, new PluginInstallJob.ArtifactProgress(resource, total, total, PluginInstallJob.ArtifactState.SUCCEEDED));
    }

    @Override
    public void transferFailed(TransferEvent event) {
        String resource = resource(event);
        PluginInstallJob.ArtifactProgress existing = progress.get(resource);
        long transferred = existing != null ? existing.transferred() : 0;
        long total = existing != null ? existing.total() : event.getResource().getContentLength();
        progress.put(resource, new PluginInstallJob.ArtifactProgress(resource, transferred, total, PluginInstallJob.ArtifactState.FAILED));
    }

    private static String resource(TransferEvent event) {
        return event.getResource().getRepositoryUrl() + event.getResource().getResourceName();
    }
}
