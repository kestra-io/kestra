package org.floworc.task.gcp.gcs;

import com.google.cloud.WriteChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.common.collect.ImmutableMap;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.floworc.core.models.tasks.RunnableTask;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Upload extends Task implements RunnableTask {
    private String from;
    private String to;

    @Override
    public RunOutput run(RunContext runContext) throws Exception {
        Connection connection = new Connection();

        Logger logger = runContext.logger(this.getClass());
        URI from = new URI(runContext.render(this.from));
        URI to = new URI(runContext.render(this.to));

        BlobInfo destination = BlobInfo
            .newBuilder(BlobId.of(to.getScheme().equals("gs") ? to.getAuthority() : to.getScheme(), to.getPath().substring(1)))
            .build();

        logger.debug("Upload from '{}' to '{}'", from, to);

        InputStream data = runContext.uriToInputStream(from);

        try (WriteChannel writer = connection.of().writer(destination)) {
            byte[] buffer = new byte[10_240];

            int limit;
            while ((limit = data.read(buffer)) >= 0) {
                writer.write(ByteBuffer.wrap(buffer, 0, limit));
            }
        }

        return RunOutput
            .builder()
            .outputs(ImmutableMap.of("uri", new URI("gs://" + destination.getBucket() + "/" + destination.getName())))
            .build();
    }
}
