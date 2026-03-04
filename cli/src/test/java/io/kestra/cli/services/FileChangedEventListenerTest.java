package io.kestra.cli.services;

import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.kestra.core.utils.Rethrow.throwRunnable;
import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest(environments = {"test", "file-watch"}, transactional = false)
class FileChangedEventListenerTest {
    public static final String FILE_WATCH = "build/file-watch";
    @Inject
    private FileChangedEventListener fileWatcher;

    @Inject
    private FlowRepositoryInterface flowRepository;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AtomicBoolean started = new AtomicBoolean(false);

    @BeforeAll
    static void setup() throws IOException {
        if (!Files.exists(Path.of(FILE_WATCH))) {
            Files.createDirectories(Path.of(FILE_WATCH));
        }
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (Files.exists(Path.of(FILE_WATCH))) {
            FileUtils.cleanDirectory(Path.of(FILE_WATCH).toFile());
            FileUtils.deleteDirectory(Path.of(FILE_WATCH).toFile());
        }
    }

    @BeforeEach
    void beforeEach() throws Exception {
        if (started.compareAndSet(false, true)) {
            executorService.execute(throwRunnable(() -> fileWatcher.startListeningFromConfig()));
        }
    }

    @FlakyTest
    @Test
    void test() throws IOException, TimeoutException {
        var tenant = TestsUtils.randomTenant(FileChangedEventListenerTest.class.getSimpleName(), "test");
        // remove the flow if it already exists
        flowRepository.findByIdWithSource(tenant, "io.kestra.tests.watch", "myflow").ifPresent(flow -> flowRepository.delete(flow));

        // create a basic flow
        String flow = """
            id: myflow
            namespace: io.kestra.tests.watch

            tasks:
              - id: hello
                type: io.kestra.plugin.core.log.Log
                message: Hello World! 🚀
            """;

        GenericFlow genericFlow = GenericFlow.fromYaml(tenant, flow);
        Files.write(Path.of(FILE_WATCH + "/" + genericFlow.uidWithoutRevision() + ".yaml"), flow.getBytes());
        Await.until(
            () -> flowRepository.findById(tenant, "io.kestra.tests.watch", "myflow").isPresent(),
            Duration.ofMillis(100),
            Duration.ofSeconds(10)
        );
        Flow myflow = flowRepository.findById(tenant, "io.kestra.tests.watch", "myflow").orElseThrow();
        assertThat(myflow.getTasks()).hasSize(1);
        assertThat(myflow.getTasks().getFirst().getId()).isEqualTo("hello");
        assertThat(myflow.getTasks().getFirst().getType()).isEqualTo("io.kestra.plugin.core.log.Log");

        // delete the flow
        Files.delete(Path.of(FILE_WATCH + "/" + genericFlow.uidWithoutRevision() + ".yaml"));
        Await.until(
            () -> flowRepository.findById(tenant, "io.kestra.tests.watch", "myflow").isEmpty(),
            Duration.ofMillis(100),
            Duration.ofSeconds(10)
        );
    }

    @FlakyTest
    @Test
    void testWithPluginDefault() throws IOException, TimeoutException {
        var tenant = TestsUtils.randomTenant(FileChangedEventListenerTest.class.getName(), "testWithPluginDefault");
        // remove the flow if it already exists
        flowRepository.findByIdWithSource(tenant, "io.kestra.tests.watch", "pluginDefault").ifPresent(flow -> flowRepository.delete(flow));

        // create a flow with plugin default
        String pluginDefault = """
            id: pluginDefault
            namespace: io.kestra.tests.watch

            tasks:
              - id: helloWithDefault
                type: io.kestra.plugin.core.log.Log

            pluginDefaults:
              - type: io.kestra.plugin.core.log.Log
                values:
                  message: Hello World!
            """;
        GenericFlow genericFlow = GenericFlow.fromYaml(tenant, pluginDefault);
        Files.write(Path.of(FILE_WATCH + "/" + genericFlow.uidWithoutRevision() + ".yaml"), pluginDefault.getBytes());
        Await.until(
            () -> flowRepository.findById(tenant, "io.kestra.tests.watch", "pluginDefault").isPresent(),
            Duration.ofMillis(100),
            Duration.ofSeconds(10)
        );
        Flow pluginDefaultFlow = flowRepository.findById(tenant, "io.kestra.tests.watch", "pluginDefault").orElseThrow();
        assertThat(pluginDefaultFlow.getTasks()).hasSize(1);
        assertThat(pluginDefaultFlow.getTasks().getFirst().getId()).isEqualTo("helloWithDefault");
        assertThat(pluginDefaultFlow.getTasks().getFirst().getType()).isEqualTo("io.kestra.plugin.core.log.Log");

        // delete both files
        Files.delete(Path.of(FILE_WATCH + "/" + genericFlow.uidWithoutRevision() + ".yaml"));
        Await.until(
            () -> flowRepository.findById(tenant, "io.kestra.tests.watch", "pluginDefault").isEmpty(),
            Duration.ofMillis(100),
            Duration.ofSeconds(10)
        );
    }
}
