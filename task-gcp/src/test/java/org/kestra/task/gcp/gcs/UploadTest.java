package org.kestra.task.gcp.gcs;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.test.annotation.MicronautTest;
import org.kestra.core.runners.RunContext;
import org.kestra.core.runners.RunOutput;
import org.kestra.core.storages.StorageInterface;
import org.kestra.core.storages.StorageObject;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class UploadTest {
    @Inject
    private StorageInterface storageInterface;

    @Inject
    private ApplicationContext applicationContext;

    @Value("${kestra.tasks.gcs.bucket}")
    private String bucket;

    @Test
    void fromStorage() throws Exception {
        StorageObject source = storageInterface.put(
            new URI("/" + FriendlyId.createFriendlyId()),
            new FileInputStream(new File(Objects.requireNonNull(UploadTest.class.getClassLoader()
                .getResource("application.yml"))
                .toURI()))
        );

        Upload task = Upload.builder()
            .from(source.getUri().toString())
            .to("gs://{{bucket}}/tasks/gcp/upload/get2.yml")
            .build();

        RunOutput run = task.run(runContext());

        assertThat(run.getOutputs().get("uri"), is(new URI("gs://" +  bucket + "/tasks/gcp/upload/get2.yml")));
    }

    @Test
    void fromRemoteUrl() throws Exception {
        Upload task = Upload.builder()
            .from("http://www.google.com")
            .to("gs://{{bucket}}/tasks/gcp/upload/google.html")
            .build();

        RunOutput run = task.run(runContext());

        assertThat(run.getOutputs().get("uri"), is(new URI("gs://" +  bucket + "/tasks/gcp/upload/google.html")));
    }

    private RunContext runContext() {
        return new RunContext(
            this.applicationContext,
            ImmutableMap.of(
                "bucket", this.bucket
            )
        );
    }
}