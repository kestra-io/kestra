package io.kestra.cli.commands.sys;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicReference;

@CommandLine.Command(
    name = "restore-flow-listeners",
    description = {"restore state-store for FlowListeners",
        "Mostly usefull in case of restore of flow queue, the state store need to be init to avoid sending old revisions."
    }
)
@Slf4j
public class FlowListenersRestoreCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Option(names = {"--timeout"}, description = "Timeout before quit, considering we complete the restore")
    private Duration timeout = Duration.ofSeconds(60);

    @Override
    public Integer call() throws Exception {
        super.call();

        FlowListenersInterface flowListeners = applicationContext.getBean(FlowListenersInterface.class);
        AtomicReference<ZonedDateTime> lastTime = new AtomicReference<>(ZonedDateTime.now());

        flowListeners.run();
        flowListeners.listen(flows -> {
            long count = flows.stream().filter(flow -> !flow.isDeleted()).count();

            stdOut("Received {0} active flows", count);

            if (count > 0) {
                lastTime.set(ZonedDateTime.now());
            }
        });

        // we can't know when it's over, wait no more flow received
        Await.until(() -> lastTime.get().compareTo(ZonedDateTime.now().minus(this.timeout)) < 0);

        return 0;
    }
}
