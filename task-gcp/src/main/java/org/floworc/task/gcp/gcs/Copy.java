package org.floworc.task.gcp.gcs;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.common.collect.ImmutableMap;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.floworc.core.models.tasks.RunnableTask;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;
import org.slf4j.Logger;

import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Copy extends Task implements RunnableTask {
    private String from;
    private String to;

    @Builder.Default
    private boolean delete = false;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        Connection connection = new Connection();
        Logger logger = runContext.logger(this.getClass());
        URI from = new URI(runContext.render(this.from));
        URI to = new URI(runContext.render(this.to));

        BlobId source = BlobId.of(from.getScheme().equals("gs") ? from.getAuthority() : from.getScheme(), from.getPath().substring(1));

        logger.debug("Moving from '{}' to '{}'", from, to);

        Blob result = connection.of()
            .copy(Storage.CopyRequest.newBuilder()
                .setSource(source)
                .setTarget(BlobId.of(to.getAuthority(), to.getPath().substring(1)))
                .build()
            )
            .getResult();

        if (this.delete) {
            connection.of().delete(source);
        }

        return RunOutput
            .builder()
            .outputs(ImmutableMap.of("uri", new URI("gs://" + result.getBucket() + "/" + result.getName())))
            .build();
    }
}
