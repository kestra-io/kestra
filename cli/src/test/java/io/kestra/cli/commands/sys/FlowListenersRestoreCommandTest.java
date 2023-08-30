package io.kestra.cli.commands.sys;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.TaskDefaultService;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.BeforeAll;
import io.kestra.core.contexts.KestraClassLoader;
import io.kestra.core.repositories.FlowRepositoryInterface;
import org.junitpioneer.jupiter.RetryingTest;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringContains.containsString;

class FlowListenersRestoreCommandTest {

    @BeforeAll
    static void init() {
        if (!KestraClassLoader.isInit()) {
            KestraClassLoader.create(RestoreQueueCommandTest.class.getClassLoader());
        }
    }

    @RetryingTest(5)
    void run() throws InterruptedException {
        final int COUNT = 5;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            FlowRepositoryInterface flowRepository = ctx.getBean(FlowRepositoryInterface.class);
            TaskDefaultService taskDefaultService = ctx.getBean(TaskDefaultService.class);

            Thread thread = new Thread(() -> {
                Integer result = PicocliRunner.call(FlowListenersRestoreCommand.class, ctx, "--timeout=PT1S");
                assertThat(result, is(0));
            });
            thread.start();
            for (int i = 0; i < COUNT; i++) {
                Flow flow = RestoreQueueCommandTest.create();
                flowRepository.create(flow, flow.generateSource(), taskDefaultService.injectDefaults(flow));
                Thread.sleep(100);
            }

            thread.join();

            assertThat(out.toString(), containsString("Received 1 active flows"));
            assertThat(out.toString(), containsString("Received 5 active flows"));
        }
    }
}