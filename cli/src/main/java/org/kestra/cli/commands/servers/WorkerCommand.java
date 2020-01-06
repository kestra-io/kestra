package org.kestra.cli.commands.servers;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.runners.Worker;
import org.kestra.core.utils.Await;
import picocli.CommandLine;

import javax.inject.Inject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CommandLine.Command(
    name = "worker",
    description = "start a worker"
)
@Slf4j
public class WorkerCommand extends AbstractCommand {
    private ExecutorService poolExecutor = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder().setNameFormat("worker-%d").build()
    );

    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Option(names = {"-t", "--thread"}, description = "the number of concurrent threads to launch")
    private int thread = Runtime.getRuntime().availableProcessors();

    public WorkerCommand() {
        super(true);
    }

    @Override
    public void run() {
        super.run();

        for (int i = 0; i < thread; i++) {
            poolExecutor.execute(applicationContext.getBean(Worker.class));
        }

        log.info("Workers started with {} thread(s)", this.thread);

        Await.until(() -> !this.applicationContext.isRunning());
    }
}