package org.floworc.task.gcp.bigquery;

import com.devskiller.friendly_id.FriendlyId;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.Schema;
import com.google.common.collect.ImmutableMap;
import io.micronaut.context.annotation.Value;
import io.micronaut.test.annotation.MicronautTest;
import org.floworc.core.Utils;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.RunOutput;
import org.floworc.core.storages.StorageInterface;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class LoadFromGcsTest {
    @Inject
    private StorageInterface storageInterface;

    @Value("${floworc.tasks.bigquery.project}")
    private String project;

    @Value("${floworc.tasks.bigquery.dataset}")
    private String dataset;

    @Test
    void fromJson() throws Exception {
        LoadFromGcs task = LoadFromGcs.builder()
            .id(LoadFromGcsTest.class.getSimpleName())
            .type(LoadFromGcs.class.getName())
            .from(Collections.singletonList(
                "gs://cloud-samples-data/bigquery/us-states/us-states.json"
            ))
            .destinationTable(project + "." + dataset + "." + FriendlyId.createFriendlyId())
            .format(AbstractLoad.Format.JSON)
            .schema(Schema.of(
                Field.of("name", LegacySQLTypeName.STRING),
                Field.of("post_abbr", LegacySQLTypeName.STRING)
            ))
            .build();

        RunContext runContext = Utils.mockRunContext(storageInterface, task, ImmutableMap.of());

        RunOutput run = task.run(runContext);
        assertThat(run.getOutputs().get("rows"), is(50L));
    }
}