package org.floworc.cli.commands;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.floworc.core.runners.Worker;
import picocli.CommandLine;

import javax.inject.Inject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CommandLine.Command(
    name = "worker",
    description = "start a worker"
)
@Slf4j
public class WorkerCommand implements Runnable {
    private ExecutorService poolExecutor = Executors.newCachedThreadPool();

    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Option(names = {"-t", "--thread"}, description = "the number of concurrent threads to launch")
    private int thread = Runtime.getRuntime().availableProcessors();

    public void run() {
        for (int i = 0; i < thread; i++) {
            poolExecutor.execute(applicationContext.getBean(Worker.class));
        }

        log.info("Workers started with {} thread(s)", this.thread);
    }
}