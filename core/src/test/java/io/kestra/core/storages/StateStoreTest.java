package io.kestra.core.storages;

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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
public class StateStoreTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void all() throws IOException, ResourceExpiredException {
        RunContext runContext = runContext();

        String state = IdUtils.create();
        runContext.stateStore().putState(state, "some-name", "my-taskrun-value", "my-value".getBytes());

        assertThat(runContext.stateStore().getState(state, "some-name", "my-taskrun-value").readAllBytes(), is("my-value".getBytes()));

        RunContext.FlowInfo flowInfo = runContext.flowInfo();
        String key = flowInfo.id() + "_states_" + state + "_some-name_" + Hashing.hashToString("my-taskrun-value");
        assertThat(runContext.namespaceKv(flowInfo.namespace()).getValue(key).get().value(), is("my-value".getBytes()));

        runContext.stateStore().deleteState(state, "some-name", "my-taskrun-value");

        FileNotFoundException fileNotFoundException = Assertions.assertThrows(FileNotFoundException.class, () -> runContext.stateStore().getState(state, "some-name", "my-taskrun-value"));
        assertThat(fileNotFoundException.getMessage(), is("State " + key + " not found"));
    }

    @Test
    void getState_FromOldStateStore_ShouldRelocateToKvStore() throws IOException, ResourceExpiredException {
        RunContext runContext = runContext();

        String state = IdUtils.create();
        Assertions.assertThrows(FileNotFoundException.class, () -> runContext.stateStore().getState(state, "some-name", "my-taskrun-value"));

        RunContext.FlowInfo flowInfo = runContext.flowInfo();
        URI oldStateStoreFileUri = URI.create("kestra:/" + flowInfo.namespace().replace(".", "/") + "/" + flowInfo.id() + "/states/" + state + "/" + Hashing.hashToString("my-taskrun-value") + "/some-name");
        byte[] expectedContent = "from-old-state".getBytes();
        runContext.storage().putFile(new ByteArrayInputStream(expectedContent), oldStateStoreFileUri);

        String key = flowInfo.id() + "_states_" + state + "_some-name_" + Hashing.hashToString("my-taskrun-value");
        assertThat(runContext.storage().getFile(oldStateStoreFileUri).readAllBytes(), is(expectedContent));

        assertThat(runContext.stateStore().getState(state, "some-name", "my-taskrun-value").readAllBytes(), is(expectedContent));

        Assertions.assertThrows(FileNotFoundException.class, () -> runContext.storage().getFile(oldStateStoreFileUri));
        assertThat(runContext.namespaceKv(flowInfo.namespace()).getValue(key).get().value(), is(expectedContent));
    }

    @Test
    void deleteState_FromOldStateStore() throws IOException {
        RunContext runContext = runContext();

        String state = IdUtils.create();

        RunContext.FlowInfo flowInfo = runContext.flowInfo();
        URI oldStateStoreFileUri = URI.create("kestra:/" + flowInfo.namespace().replace(".", "/") + "/" + flowInfo.id() + "/states/" + state + "/" + Hashing.hashToString("my-taskrun-value") + "/some-name");
        byte[] expectedContent = "from-old-state".getBytes();
        runContext.storage().putFile(new ByteArrayInputStream(expectedContent), oldStateStoreFileUri);

        assertThat(runContext.storage().getFile(oldStateStoreFileUri).readAllBytes(), is(expectedContent));

        runContext.stateStore().deleteState(state, "some-name", "my-taskrun-value");

        Assertions.assertThrows(FileNotFoundException.class, () -> runContext.storage().getFile(oldStateStoreFileUri));
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

        assertThat(runContext.stateStore().getState(state, "a-name", "a-taskrun-value").readAllBytes(), is("aa-value".getBytes()));
        assertThat(runContext.stateStore().getState(state, "a-name", "b-taskrun-value").readAllBytes(), is("ab-value".getBytes()));
        assertThat(runContext.stateStore().getState(state, "b-name", "a-taskrun-value").readAllBytes(), is("ba-value".getBytes()));
        assertThat(runContext.stateStore().getState(state, "b-name", "b-taskrun-value").readAllBytes(), is("bb-value".getBytes()));
        assertThat(runContext.stateStore().getState(state, null, "a-taskrun-value").readAllBytes(), is("0a-value".getBytes()));
        assertThat(runContext.stateStore().getState(state, null, "b-taskrun-value").readAllBytes(), is("0b-value".getBytes()));
        assertThat(runContext.stateStore().getState(state, "a-name", null).readAllBytes(), is("a0-value".getBytes()));
        assertThat(runContext.stateStore().getState(state, "b-name", null).readAllBytes(), is("b0-value".getBytes()));
    }

    private RunContext runContext() {
        return TestsUtils.mockRunContext(runContextFactory, Log.builder().id("log").type(Log.class.getName()).message("logging").build(), null);
    }
}
