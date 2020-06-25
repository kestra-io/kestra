package org.kestra.runner.kafka;

import com.bakdata.fluent_kafka_streams_tests.TestInput;
import com.bakdata.fluent_kafka_streams_tests.TestOutput;
import com.bakdata.fluent_kafka_streams_tests.TestTopology;
import io.micronaut.test.annotation.MicronautTest;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.repositories.LocalFlowRepositoryLoader;
import org.kestra.core.runners.WorkerTaskResult;
import org.kestra.core.tasks.flows.Parallel;
import org.kestra.core.utils.TestsUtils;
import org.kestra.runner.kafka.configs.ClientConfig;
import org.kestra.runner.kafka.serializers.JsonSerde;
import org.kestra.runner.kafka.services.KafkaAdminService;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ListIterator;
import java.util.Properties;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@MicronautTest
@Slf4j
class KafkaExecutorTest {
    @Inject
    KafkaExecutor stream;
    
    @Inject
    ClientConfig clientConfig;

    @Inject
    KafkaAdminService kafkaAdminService;

    @Inject
    LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    FlowRepositoryInterface flowRepository;
    
    TestTopology<String, String> testTopology;
    
    @BeforeEach
    void init() throws IOException, URISyntaxException {
        TestsUtils.loads(repositoryLoader);

        Properties properties = new Properties();
        properties.putAll(clientConfig.getProperties());
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "unit-test");

        testTopology = new TestTopology<>(stream.topology(), properties);
        testTopology.start();
    }

    @AfterEach
    void tear() {
        if (this.testTopology != null) {
            testTopology.stop();
        }
    }

    @Test
    void standard() {
        Flow flow = flowRepository.findById("org.kestra.tests", "logs").orElseThrow();
        this.flowInput().add(flow.uid(), flow);

        createExecution(flow);

        // task
        runningAndSuccessSequential(flow, 0);
        runningAndSuccessSequential(flow, 1);
        runningAndSuccessSequential(flow, 2);

        ProducerRecord<String, Execution> executionRecord = executionOutput().readOneRecord();

        assertThat(executionRecord.value().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    void concurrent() {
        Flow flow = flowRepository.findById("org.kestra.tests", "logs").orElseThrow();
        this.flowInput().add(flow.uid(), flow);

        createExecution(flow);

        // task
        runningAndSuccessSequential(flow, 0);
        runningAndSuccessSequential(flow, 1);

        // next
        ProducerRecord<String, Execution> executionRecord = executionOutput().readOneRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(3));
        assertThat(executionRecord.value().getTaskRunList().get(2).getState().getCurrent(), is(State.Type.CREATED));


        Task task = flow.getTasks().get(2);
        TaskRun taskRun = executionRecord.value().getTaskRunList().get(2);

        // concurrent
        this.changeStatus(task, taskRun, State.Type.RUNNING);
        this.changeStatus(task, taskRun, State.Type.SUCCESS);

        executionRecord = executionOutput().readOneRecord();
        executionRecord = executionOutput().readOneRecord();
        executionRecord = executionOutput().readOneRecord();

        assertThat(executionRecord.value().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    void parallel() {
        Flow flow = flowRepository.findById("org.kestra.tests", "parallel").orElseThrow();
        this.flowInput().add(flow.uid(), flow);

        createExecution(flow);
        Parallel parent = (Parallel) flow.getTasks().get(0);
        Task last = flow.getTasks().get(1);

        // parent > worker > RUNNING
        ProducerRecord<String, Execution> executionRecord = executionOutput().readOneRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(1));
        this.changeStatus(parent, executionRecord.value().getTaskRunList().get(0), State.Type.RUNNING);

        // parent > execution RUNNING
        executionRecord = executionOutput().readOneRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(1));
        assertThat(executionRecord.value().getTaskRunList().get(0).getState().getCurrent(), is(State.Type.RUNNING));

        // Task > all RUNNING
        for (ListIterator<Task> it = parent.getTasks().listIterator(); it.hasNext(); ) {
            int index = it.nextIndex();
            Task next = it.next();

            executionRecord = executionOutput().readOneRecord();

            // Task > CREATED
            assertThat(executionRecord.value().getTaskRunList(), hasSize(index + 2));
            assertThat(executionRecord.value().getTaskRunList().get(index + 1).getState().getCurrent(), is(State.Type.CREATED));

            // Task > RUNNING
            this.changeStatus(next, executionRecord.value().getTaskRunList().get(index + 1), State.Type.RUNNING);

            executionRecord = executionOutput().readOneRecord();
            assertThat(executionRecord.value().getTaskRunList().get(index + 1).getState().getCurrent(), is(State.Type.RUNNING));
        }

        // Task > all SUCCESS
        for (ListIterator<Task> it = parent.getTasks().listIterator(); it.hasNext(); ) {
            int index = it.nextIndex();
            Task next = it.next();

            this.changeStatus(next, executionRecord.value().getTaskRunList().get(index + 1), State.Type.SUCCESS);
        }

        // Task > read SUCCESS
        for (int index = 0; index < parent.getTasks().size() ; index++) {
            executionRecord = executionOutput().readOneRecord();
            assertThat(executionRecord.value().getTaskRunList().get(index + 1).getState().getCurrent(), is(State.Type.SUCCESS));
        }

        // parent terminated
        executionRecord = executionOutput().readOneRecord();
        assertThat(executionRecord.value().getTaskRunList().get(0).getState().getCurrent(), is(State.Type.SUCCESS));

        // last
        executionRecord = executionOutput().readOneRecord();
        this.changeStatus(last, executionRecord.value().getTaskRunList().get(7), State.Type.RUNNING);

        this.changeStatus(last, executionRecord.value().getTaskRunList().get(7), State.Type.RUNNING);
        executionRecord = executionOutput().readOneRecord();
        assertThat(executionRecord.value().getTaskRunList().get(7).getState().getCurrent(), is(State.Type.RUNNING));

        this.changeStatus(last, executionRecord.value().getTaskRunList().get(7), State.Type.SUCCESS);
        executionRecord = executionOutput().readOneRecord();
        assertThat(executionRecord.value().getTaskRunList().get(7).getState().getCurrent(), is(State.Type.SUCCESS));

        // ok
        executionRecord = executionOutput().readOneRecord();
        assertThat(executionRecord.value().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    private void createExecution(Flow flow) {
        Execution execution = Execution.builder()
            .id("unittest")
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .state(new State())
            .build();

        this.executionInput().add("unittest", execution);
    }

    private void runningAndSuccessSequential(Flow flow, int index) {
        ProducerRecord<String, Execution> executionRecord;
        Task task = flow.getTasks().get(index);

        executionRecord = executionOutput().readOneRecord();

        // CREATED
        assertThat(executionRecord.value().getTaskRunList(), hasSize(index + 1));
        assertThat(executionRecord.value().getTaskRunList().get(index).getState().getCurrent(), is(State.Type.CREATED));


        // RUNNING
        this.changeStatus(task, executionRecord.value().getTaskRunList().get(index), State.Type.RUNNING);

        executionRecord = executionOutput().readOneRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(index + 1));
        assertThat(executionRecord.value().getTaskRunList().get(index).getState().getCurrent(), is(State.Type.RUNNING));

        // SUCCESS
        this.changeStatus(task, executionRecord.value().getTaskRunList().get(index), State.Type.SUCCESS);

        executionRecord = executionOutput().readOneRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(index + 1));
        assertThat(executionRecord.value().getTaskRunList().get(index).getState().getCurrent(), is(State.Type.SUCCESS));
    }

    private void changeStatus(Task task, TaskRun taskRun, State.Type state) {
        this.workerTaskResultInput()
            .add("unittest", WorkerTaskResult.builder()
                .task(task)
                .taskRun(taskRun.withState(state))
                .build()
            );

    }

    private TestInput<String, Flow> flowInput() {
        return this.testTopology
            .input(kafkaAdminService.getTopicName(Flow.class))
            .withSerde(Serdes.String(), JsonSerde.of(Flow.class));
    }

    private TestInput<String, Execution> executionInput() {
        return this.testTopology
            .input(kafkaAdminService.getTopicName(Execution.class))
            .withSerde(Serdes.String(), JsonSerde.of(Execution.class));
    }

    private TestInput<String, WorkerTaskResult> workerTaskResultInput() {
        return this.testTopology
            .input(kafkaAdminService.getTopicName(WorkerTaskResult.class))
            .withSerde(Serdes.String(), JsonSerde.of(WorkerTaskResult.class));
    }

    private TestOutput<String, Execution> executionOutput() {
        return this.testTopology
            .streamOutput(kafkaAdminService.getTopicName(Execution.class))
            .withSerde(Serdes.String(), JsonSerde.of(Execution.class));
    }
}
