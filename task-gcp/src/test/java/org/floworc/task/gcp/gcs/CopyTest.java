package org.floworc.task.gcp.gcs;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.test.annotation.MicronautTest;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;
import org.floworc.core.storages.AbstractLocalStorageTest;
import org.floworc.core.storages.StorageInterface;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.FileInputStream;
import java.net.URI;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class CopyTest {
    @Inject
    private StorageInterface storageInterface;

    @Inject
    private ApplicationContext applicationContext;


    @Value("${floworc.tasks.gcs.bucket}")
    private String bucket;

    @Test
    void run() throws Exception {
        RunContext runContext = new RunContext(
            this.applicationContext,
            ImmutableMap.of(
                "bucket", this.bucket
            )
        );

        storageInterface.put(
            new URI("file/storage/get.yml"),
            new FileInputStream(Objects.requireNonNull(AbstractLocalStorageTest.class.getClassLoader().getResource("application.yml")).getFile())
        );

        Copy task = Copy.builder()
            .from("gs://{{bucket}}/file/storage/get.yml")
            .to("gs://{{bucket}}/file/storage/get2.yml")
            .build();

        RunOutput run = task.run(runContext);

        assertThat(run.getOutputs().get("uri"), is(new URI("gs://" + bucket + "/file/storage/get2.yml")));
    }
}