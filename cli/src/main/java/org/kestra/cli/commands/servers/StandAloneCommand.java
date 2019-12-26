package org.kestra.cli.commands.servers;

import lombok.extern.slf4j.Slf4j;
import org.kestra.core.runners.StandAloneRunner;
import org.kestra.core.utils.Await;
import picocli.CommandLine;

import javax.inject.Inject;

@CommandLine.Command(
    name = "standalone",
    description = "start a standalone server"
)
@Slf4j
public class StandAloneCommand extends AbstractServerCommand {
    @Inject
    private StandAloneRunner standAloneRunner;

    @Override
    public void run() {
        super.run();

        standAloneRunner.run();

        Await.until(() -> !this.standAloneRunner.isRunning());
    }
}