package io.kestra.cli.commands.sys;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import io.kestra.cli.AbstractCommand;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.core.utils.Await;
import picocli.CommandLine;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;

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
    private Duration timeout = Duration.ofSeconds(15);

    public FlowListenersRestoreCommand() {
        super(false);
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        FlowListenersInterface flowListeners = applicationContext.getBean(FlowListenersInterface.class);

        AtomicReference<ZonedDateTime> lastTime = new AtomicReference<>(ZonedDateTime.now());

        flowListeners.listen(flows -> {
            stdOut("Received {0} flows", flows.size());

            lastTime.set(ZonedDateTime.now());
        });

        // we can't know when it's over to wait no more flow received
        Await.until(() -> lastTime.get().compareTo(ZonedDateTime.now().minus(this.timeout)) < 0);

        return 0;
    }
}
