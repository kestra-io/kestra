package io.kestra.cli.commands.sys;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.kestra.core.contexts.KestraClassLoader;
import io.kestra.core.repositories.FlowRepositoryInterface;

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

    @Test
    void run() throws InterruptedException {
        final int COUNT = 5;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            FlowRepositoryInterface flowRepository = ctx.getBean(FlowRepositoryInterface.class);

            Thread thread = new Thread(() -> {
                Integer result = PicocliRunner.call(FlowListenersRestoreCommand.class, ctx, "--timeout=PT1S");
                assertThat(result, is(0));
            });
            thread.start();

            for (int i = 0; i < COUNT; i++) {
                flowRepository.create(RestoreQueueCommandTest.create());
                Thread.sleep(100);
            }

            thread.join();

            assertThat(out.toString(), containsString("Received 1 flows"));
            assertThat(out.toString(), containsString("Received 5 flows"));
        }
    }
}