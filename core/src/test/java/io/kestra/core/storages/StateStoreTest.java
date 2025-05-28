package io.kestra.core.storages;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.exceptions.MigrationRequiredException;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.Hashing;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.log.Log;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public class StateStoreTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void all() throws IOException, ResourceExpiredException {
        RunContext runContext = runContext();

        String state = IdUtils.create();
        runContext.stateStore().putState(state, "some-name", "my-taskrun-value", "my-value".getBytes());

        assertThat(runContext.stateStore().getState(state, "some-name", "my-taskrun-value").readAllBytes()).isEqualTo("my-value".getBytes());

        RunContext.FlowInfo flowInfo = runContext.flowInfo();
        String key = flowInfo.id() + "_states_" + state + "_some-name_" + Hashing.hashToString("my-taskrun-value");
        assertThat(runContext.namespaceKv(flowInfo.namespace()).getValue(key).get().value()).isEqualTo("my-value".getBytes());

        runContext.stateStore().deleteState(state, "some-name", "my-taskrun-value");

        FileNotFoundException fileNotFoundException = Assertions.assertThrows(FileNotFoundException.class, () -> runContext.stateStore().getState(state, "some-name", "my-taskrun-value"));
        assertThat(fileNotFoundException.getMessage()).isEqualTo("State " + key + " not found");
    }

    @Test
    void getState_WithOldStateStore_ShouldThrowMigrationException() throws IOException, ResourceExpiredException {
        RunContext runContext = runContext();
        String state = IdUtils.create();

        RunContext.FlowInfo flowInfo = runContext.flowInfo();
        URI oldStateStoreFileUri = URI.create("kestra:/" + flowInfo.namespace().replace(".", "/") + "/" + flowInfo.id() + "/states/" + state + "/" + Hashing.hashToString("my-taskrun-value") + "/some-name");
        byte[] expectedContent = "from-old-state".getBytes();
        runContext.storage().putFile(new ByteArrayInputStream(expectedContent), oldStateStoreFileUri);

        String key = flowInfo.id() + "_states_" + state + "_some-name_" + Hashing.hashToString("my-taskrun-value");
        assertThat(runContext.storage().getFile(oldStateStoreFileUri).readAllBytes()).isEqualTo(expectedContent);

        MigrationRequiredException migrationRequiredException = Assertions.assertThrows(MigrationRequiredException.class, () -> runContext.stateStore().getState(state, "some-name", "my-taskrun-value"));
        assertThat(migrationRequiredException.getMessage()).isEqualTo("It looks like the State Store migration hasn't been run, please run the `/app/kestra sys state-store migrate` command before.");

        assertThat(runContext.namespaceKv(flowInfo.namespace()).getValue(key).isEmpty()).isTrue();
    }

    @Test
    void subNameAndTaskrunValueOptional() throws IOException, ResourceExpiredException {
        RunContext runContext = runContext();

        String state = IdUtils.create();
        runContext.stateStore().putState(state, "a-name", "a-taskrun-value", "aa-value".getBytes());
        runContext.stateStore().putState(state, "a-name", "b-taskrun-value", "ab-value".getBytes());
        runContext.stateStore().putState(state, "b-name", "a-taskrun-value", "ba-value".getBytes());
        runContext.stateStore().putState(state, "b-name", "b-taskrun-value", "bb-value".getBytes());
        runContext.stateStore().putState(state, null, "a-taskrun-value", "0a-value".getBytes());
        runContext.stateStore().putState(state, null, "b-taskrun-value", "0b-value".getBytes());
        runContext.stateStore().putState(state, "a-name", null, "a0-value".getBytes());
        runContext.stateStore().putState(state, "b-name", null, "b0-value".getBytes());

        assertThat(runContext.stateStore().getState(state, "a-name", "a-taskrun-value").readAllBytes()).isEqualTo("aa-value".getBytes());
        assertThat(runContext.stateStore().getState(state, "a-name", "b-taskrun-value").readAllBytes()).isEqualTo("ab-value".getBytes());
        assertThat(runContext.stateStore().getState(state, "b-name", "a-taskrun-value").readAllBytes()).isEqualTo("ba-value".getBytes());
        assertThat(runContext.stateStore().getState(state, "b-name", "b-taskrun-value").readAllBytes()).isEqualTo("bb-value".getBytes());
        assertThat(runContext.stateStore().getState(state, null, "a-taskrun-value").readAllBytes()).isEqualTo("0a-value".getBytes());
        assertThat(runContext.stateStore().getState(state, null, "b-taskrun-value").readAllBytes()).isEqualTo("0b-value".getBytes());
        assertThat(runContext.stateStore().getState(state, "a-name", null).readAllBytes()).isEqualTo("a0-value".getBytes());
        assertThat(runContext.stateStore().getState(state, "b-name", null).readAllBytes()).isEqualTo("b0-value".getBytes());
    }

    private RunContext runContext() {
        return TestsUtils.mockRunContext(runContextFactory, Log.builder().id("log").type(Log.class.getName()).message("logging").build(), null);
    }
}
