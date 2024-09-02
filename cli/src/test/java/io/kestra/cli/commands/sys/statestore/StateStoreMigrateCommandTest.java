package io.kestra.cli.commands.sys.statestore;

import com.devskiller.friendly_id.FriendlyId;
import io.kestra.core.exceptions.MigrationRequiredException;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StateStore;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.Hashing;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.Slugify;
import io.kestra.plugin.core.log.Log;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;

class StateStoreMigrateCommandTest {
    @Test
    void runMigration() throws IOException, ResourceExpiredException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).environments("test").start()) {
            FlowRepositoryInterface flowRepository = ctx.getBean(FlowRepositoryInterface.class);

            Flow flow = Flow.builder()
                .tenantId("my-tenant")
                .id("a-flow")
                .namespace("some.valid.namespace." + ((int) (Math.random() * 1000000)))
                .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("logging").build()))
                .build();
            flowRepository.create(flow, flow.generateSource(), flow);

            StorageInterface storage = ctx.getBean(StorageInterface.class);
            String tenantId = flow.getTenantId();
            URI oldStateStoreUri = URI.create("/" + flow.getNamespace().replace(".", "/") + "/" + Slugify.of("a-flow") + "/states/my-state/" + Hashing.hashToString("my-taskrun-value") + "/sub-name");
            storage.put(
                tenantId,
                oldStateStoreUri,
                new ByteArrayInputStream("my-value".getBytes())
            );
            assertThat(
                storage.exists(tenantId, oldStateStoreUri),
                is(true)
            );

            RunContext runContext = ctx.getBean(RunContextFactory.class).of(flow, Map.of("flow", Map.of(
                "tenantId", tenantId,
                "id", flow.getId(),
                "namespace", flow.getNamespace()
            )));
            StateStore stateStore = new StateStore(runContext, true);
            Assertions.assertThrows(MigrationRequiredException.class, () -> stateStore.getState(true, "my-state", "sub-name", "my-taskrun-value"));

            String[] args = {};
            Integer call = PicocliRunner.call(StateStoreMigrateCommand.class, ctx, args);

            assertThat(new String(stateStore.getState(true, "my-state", "sub-name", "my-taskrun-value").readAllBytes()), is("my-value"));
            assertThat(
                storage.exists(tenantId, oldStateStoreUri),
                is(false)
            );

            assertThat(call, is(0));
        }
    }
}
