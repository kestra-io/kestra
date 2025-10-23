package io.kestra.jdbc.runner;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class JdbcQueuePollerBenchmark {
    
    public static final JdbcQueueConfiguration DEFAULT_POLLER_CONFIG = new JdbcQueueConfiguration(
        Duration.ofMillis(25),
        Duration.ofMillis(500),
        Duration.ofSeconds(60),
        100,
        5,
        true
    );
    List<JdbcQueueConfiguration.Step> STEPS = DEFAULT_POLLER_CONFIG.computeSteps();
    
    private JdbcQueuePoller poller;
    
    @Setup(Level.Invocation)
    public void setup() {
        poller = new JdbcQueuePoller(DEFAULT_POLLER_CONFIG, () -> 1);
       
    }
    
    @Benchmark
    public void testPollOnce() {
        poller.pollOnce(ZonedDateTime.now(), STEPS);
    }
}
