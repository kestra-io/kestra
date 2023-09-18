package io.kestra.storage.local;

import com.google.common.io.CharStreams;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.types.Schedule;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tasks.log.Log;
import io.kestra.core.utils.IdUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import jakarta.inject.Inject;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class LocalStorageTest {
    @Inject
    StorageInterface storageInterface;

    private URI putFile(URL resource, String path) throws Exception {
        return storageInterface.put(
            null,
            new URI(path),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );
    }

    @Test
    void get() throws Exception {
        String prefix = IdUtils.create();

        URL resource = LocalStorageTest.class.getClassLoader().getResource("application.yml");
        String content = CharStreams.toString(new InputStreamReader(new FileInputStream(Objects.requireNonNull(resource).getFile())));

        this.putFile(resource, "/" + prefix + "/storage/get.yml");

        URI item = new URI("/" + prefix + "/storage/get.yml");
        InputStream get = storageInterface.get(null, item);
        assertThat(CharStreams.toString(new InputStreamReader(get)), is(content));
        assertTrue(storageInterface.exists(null, item));
        assertThat(storageInterface.size(null, item), is((long) content.length()));
        assertThat(storageInterface.lastModifiedTime(null, item), notNullValue());

        InputStream getScheme = storageInterface.get(null, new URI("kestra:///" + prefix + "/storage/get.yml"));
        assertThat(CharStreams.toString(new InputStreamReader(getScheme)), is(content));
    }

    @Test
    void missing() {
        String prefix = IdUtils.create();

        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.get(null, new URI("/" + prefix + "/storage/missing.yml"));
        });
    }

    @Test
    void put() throws Exception {
        String prefix = IdUtils.create();

        URL resource = LocalStorageTest.class.getClassLoader().getResource("application.yml");
        URI put = this.putFile(resource, "/" + prefix + "/storage/put.yml");
        InputStream get = storageInterface.get(null, new URI("/" + prefix + "/storage/put.yml"));

        assertThat(put.toString(), is(new URI("kestra:///" + prefix + "/storage/put.yml").toString()));
        assertThat(
            CharStreams.toString(new InputStreamReader(get)),
            is(CharStreams.toString(new InputStreamReader(new FileInputStream(Objects.requireNonNull(resource).getFile()))))
        );

        assertThat(storageInterface.size(null, new URI("/" + prefix + "/storage/put.yml")), is(77L));

        assertThrows(FileNotFoundException.class, () -> {
            assertThat(storageInterface.size(null, new URI("/" + prefix + "/storage/muissing.yml")), is(76L));
        });

        boolean delete = storageInterface.delete(null, put);
        assertThat(delete, is(true));

        delete = storageInterface.delete(null, put);
        assertThat(delete, is(false));

        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.get(null, new URI("/" + prefix + "/storage/put.yml"));
        });
    }

    @Test
    void deleteByPrefix() throws Exception {
        String prefix = IdUtils.create();

        URL resource = LocalStorageTest.class.getClassLoader().getResource("application.yml");

        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml"
        );

        path.forEach(throwConsumer(s -> this.putFile(resource, s)));

        List<URI> deleted = storageInterface.deleteByPrefix(null, new URI("/" + prefix + "/storage/"));

        assertThat(deleted, containsInAnyOrder(path.stream().map(s -> URI.create("kestra://" + s)).toArray()));

        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.get(null, new URI("/" + prefix + "/storage/"));
        });

        path
            .forEach(s -> {
                assertThrows(FileNotFoundException.class, () -> {
                    storageInterface.get(null, new URI(s));
                });
            });
    }

    @Test
    void deleteByPrefixNoResult() throws Exception {
        String prefix = IdUtils.create();

        List<URI> deleted = storageInterface.deleteByPrefix(null, new URI("/" + prefix + "/storage/"));
        assertThat(deleted.size(), is(0));
    }

    @Test
    void executionPrefix() {
        var flow = Flow.builder().id("flow").namespace("namespace").build();
        var execution = Execution.builder().id("execution").namespace("namespace").flowId("flow").build();
        var taskRun = TaskRun.builder().id("taskrun").namespace("namespace").flowId("flow").executionId("execution").build();

        var prefix = storageInterface.executionPrefix(flow, execution);
        assertThat(prefix, notNullValue());
        assertThat(prefix, is("/namespace/flow/executions/execution"));

        prefix = storageInterface.executionPrefix(execution);
        assertThat(prefix, notNullValue());
        assertThat(prefix, is("/namespace/flow/executions/execution"));

        prefix = storageInterface.executionPrefix(taskRun);
        assertThat(prefix, notNullValue());
        assertThat(prefix, is("/namespace/flow/executions/execution"));
    }

    @Test
    void cachePrefix() {
        var prefix = storageInterface.cachePrefix("namespace", "flow", "task", null);
        assertThat(prefix, notNullValue());
        assertThat(prefix, is("namespace/flow/task/cache"));

        prefix = storageInterface.cachePrefix("namespace", "flow", "task", "value");
        assertThat(prefix, notNullValue());
        assertThat(prefix, startsWith("namespace/flow/task/cache/"));
    }

    @Test
    void statePrefix() {
        var prefix = storageInterface.statePrefix("namespace", "flow", "name", null);
        assertThat(prefix, notNullValue());
        assertThat(prefix, is("namespace/flow/states/name"));

        prefix = storageInterface.statePrefix("namespace", "flow", "name", "value");
        assertThat(prefix, notNullValue());
        assertThat(prefix, startsWith("namespace/flow/states/name/"));
    }

    @Test
    void outputPrefix() {
        var flow = Flow.builder().id("flow").namespace("namespace").build();
        var prefix = storageInterface.outputPrefix(flow);
        assertThat(prefix, notNullValue());
        assertThat(prefix.toString(), is("///namespace/flow"));

        var log = Log.builder().id("log").type(Log.class.getName()).message("Hello").build();
        var execution = Execution.builder().id("execution").build();
        var taskRun = TaskRun.builder().id("taskrun").namespace("namespace").flowId("flow").executionId("execution").taskId("taskid").build();
        prefix = storageInterface.outputPrefix(flow, log, execution, taskRun);
        assertThat(prefix, notNullValue());
        assertThat(prefix.toString(), is("///namespace/flow/executions/execution/tasks/taskid/taskrun"));

        var triggerContext = TriggerContext.builder().namespace("namespace").flowId("flow").triggerId("trigger").build();
        var trigger = Schedule.builder().id("trigger").build();
        prefix = storageInterface.outputPrefix(triggerContext, trigger, "execution");
        assertThat(prefix, notNullValue());
        assertThat(prefix.toString(), is("///namespace/flow/executions/execution/trigger/trigger"));
    }
}
