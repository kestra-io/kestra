package org.kestra.runner.kafka;

import com.bakdata.fluent_kafka_streams_tests.TestInput;
import com.bakdata.fluent_kafka_streams_tests.TestOutput;
import com.bakdata.fluent_kafka_streams_tests.TestTopology;
import io.micronaut.test.annotation.MicronautTest;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.ExecutionKilled;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.repositories.LocalFlowRepositoryLoader;
import org.kestra.core.runners.*;
import org.kestra.core.tasks.flows.Parallel;
import org.kestra.core.utils.TestsUtils;
import org.kestra.runner.kafka.configs.ClientConfig;
import org.kestra.runner.kafka.serializers.JsonSerde;
import org.kestra.runner.kafka.services.KafkaAdminService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.ListIterator;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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

    static WorkerInstance workerInstance = workerInstance();

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

        // running most be deleted at the end
        assertThat(workerTaskRunningOutput().readOneRecord().value(), is(nullValue()));
        assertThat(workerTaskRunningOutput().readOneRecord().value(), is(nullValue()));
        assertThat(workerTaskRunningOutput().readOneRecord().value(), is(nullValue()));
        assertThat(workerTaskRunningOutput().readOneRecord(), is(nullValue()));
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
    void killed() {
        Flow flow = flowRepository.findById("org.kestra.tests", "logs").orElseThrow();
        this.flowInput().add(flow.uid(), flow);

        createExecution(flow);

        // task
        Execution execution = runningAndSuccessSequential(flow, 0);

        // multiple killed should have no impact
        createKilled(execution);
        createKilled(execution);
        createKilled(execution);

        // next
        ProducerRecord<String, Execution> executionRecord = executionOutput().readOneRecord();
        executionRecord = executionOutput().readOneRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(2));
        assertThat(executionRecord.value().getState().getCurrent(), is(State.Type.KILLING));

        Task task = flow.getTasks().get(1);
        TaskRun taskRun = executionRecord.value().getTaskRunList().get(1);

        // late arrival from worker
        this.changeStatus(task, taskRun, State.Type.RUNNING);
        this.changeStatus(task, taskRun, State.Type.SUCCESS);

        executionRecord = executionOutput().readOneRecord();
        executionRecord = executionOutput().readOneRecord();

        assertThat(executionRecord.value().getTaskRunList(), hasSize(2));
        assertThat(executionRecord.value().getTaskRunList().get(1).getState().getCurrent(), is(State.Type.SUCCESS));

        assertThat(executionOutput().readOneRecord().value().getState().getCurrent(), is(State.Type.KILLED));
        assertThat(executionOutput().readOneRecord(), is(nullValue()));
    }

    @Test
    void killedAlreadyFinished() {
        Flow flow = flowRepository.findById("org.kestra.tests", "logs").orElseThrow();
        this.flowInput().add(flow.uid(), flow);

        createExecution(flow);

        // task
        runningAndSuccessSequential(flow, 0);
        runningAndSuccessSequential(flow, 1);
        Execution execution = runningAndSuccessSequential(flow, 2);

        createKilled(execution);

        // next
        ProducerRecord<String, Execution> executionRecord = executionOutput().readOneRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(3));
        assertThat(executionRecord.value().getState().getCurrent(), is(State.Type.SUCCESS));

        assertThat(executionOutput().readOneRecord(), is(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void killedParallel(boolean killed) {
        Flow flow = flowRepository.findById("org.kestra.tests", "parallel").orElseThrow();
        this.flowInput().add(flow.uid(), flow);

        createExecution(flow);
        Parallel parent = (Parallel) flow.getTasks().get(0);
        Task firstChild = parent.getTasks().get(0);
        Task secondChild = parent.getTasks().get(1);

        // parent > worker > RUNNING
        Execution executionRecord = executionOutput().readOneRecord().value();
        assertThat(executionRecord.getTaskRunList(), hasSize(1));
        this.changeStatus(parent, executionRecord.getTaskRunList().get(0), State.Type.RUNNING);

        // parent > execution RUNNING
        executionRecord = executionOutput().readOneRecord().value();
        assertThat(executionRecord.getTaskRunList(), hasSize(1));
        assertThat(executionRecord.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.RUNNING));


        // first child > RUNNING
        executionRecord = executionOutput().readOneRecord().value();
        assertThat(executionRecord.getTaskRunList(), hasSize(2));
        assertThat(executionRecord.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.CREATED));

        this.changeStatus(firstChild, executionRecord.getTaskRunList().get(1), State.Type.RUNNING);
        executionRecord = executionOutput().readOneRecord().value();
        assertThat(executionRecord.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.RUNNING));

        // second child > CREATED
        executionRecord = executionOutput().readOneRecord().value();
        assertThat(executionRecord.getTaskRunList(), hasSize(3));
        assertThat(executionRecord.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.CREATED));

        // killed execution
        createKilled(executionRecord);
        executionRecord = executionOutput().readOneRecord().value();
        executionRecord = executionOutput().readOneRecord().value();
        assertThat(executionRecord.getState().getCurrent(), is(State.Type.KILLING));
        assertThat(executionRecord.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.KILLING));

        // change second child to RUNNING
        this.changeStatus(secondChild, executionRecord.getTaskRunList().get(2), State.Type.RUNNING);
        executionRecord = executionOutput().readOneRecord().value();
        assertThat(executionRecord.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.RUNNING));

        // kill the first & second child
        if (killed) {
            this.changeStatus(firstChild, executionRecord.getTaskRunList().get(1), State.Type.KILLED);
            this.changeStatus(secondChild, executionRecord.getTaskRunList().get(2), State.Type.KILLED);
        } else {
            this.changeStatus(firstChild, executionRecord.getTaskRunList().get(1), State.Type.SUCCESS);
            this.changeStatus(secondChild, executionRecord.getTaskRunList().get(2), State.Type.SUCCESS);
        }

        // killing state
        executionRecord = executionOutput().readOneRecord().value();
        executionRecord = executionOutput().readOneRecord().value();
        executionRecord = executionOutput().readOneRecord().value();
        executionRecord = executionOutput().readOneRecord().value();

        // control
        assertThat(executionRecord.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.KILLED));
        assertThat(executionRecord.getTaskRunList().get(1).getState().getCurrent(), is(killed ? State.Type.KILLED : State.Type.SUCCESS));
        assertThat(executionRecord.getTaskRunList().get(2).getState().getCurrent(), is(killed ? State.Type.KILLED : State.Type.SUCCESS));
        assertThat(executionRecord.getState().getCurrent(), is(State.Type.KILLED));

        assertThat(executionOutput().readOneRecord(), is(nullValue()));
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

    @Test
    void flowTrigger() {
        Flow triggerFlow = flowRepository.findById("org.kestra.tests", "trigger-flow-listener-no-inputs").orElseThrow();
        this.flowInput().add(triggerFlow.uid(), triggerFlow);

        // we add 2 version of the same triggering flow to be sure to have only the last one triggered
        Flow updateTriggerFlow = triggerFlow.withRevision(2);
        this.flowInput().add(updateTriggerFlow.uid(), updateTriggerFlow.withRevision(2));

        Flow firstFlow = flowRepository.findById("org.kestra.tests", "trigger-flow").orElseThrow();
        this.flowInput().add(firstFlow.uid(), firstFlow);

        createExecution(firstFlow);

        // task
        runningAndSuccessSequential(firstFlow, 0);

        ProducerRecord<String, Execution> firstExecution = executionOutput().readOneRecord();
        assertThat(firstExecution.value().getState().getCurrent(), is(State.Type.SUCCESS));

        ProducerRecord<String, Execution> triggerExecution = executionOutput().readOneRecord();
        assertThat(triggerExecution.value().getState().getCurrent(), is(State.Type.CREATED));

        runningAndSuccessSequential(triggerFlow, 0);

        triggerExecution = executionOutput().readOneRecord();
        assertThat(triggerExecution.value().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    void workerRebalanced() {
        Flow flow = flowRepository.findById("org.kestra.tests", "logs").orElseThrow();
        this.flowInput().add(flow.uid(), flow);
        this.workerInstanceInput().add(workerInstance.getWorkerUuid().toString(), workerInstance);

        createExecution(flow);

        Execution execution = runningAndSuccessSequential(flow, 0, State.Type.RUNNING);
        assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(this.workerTaskOutput().readOneRecord().value().getTaskRun().getState().getCurrent(), is(State.Type.CREATED));

        // declare a new worker instance
        WorkerInstance newInstance = workerInstance();
        this.workerInstanceInput().add(newInstance.getWorkerUuid().toString(), newInstance);

        // receive a new WorkTask meaning that the resend is done
        assertThat(this.workerTaskOutput().readOneRecord().value().getTaskRun().getState().getCurrent(), is(State.Type.CREATED));
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

    private void createKilled(Execution execution) {
        ExecutionKilled executionKilled = ExecutionKilled.builder()
            .executionId(execution.getId())
            .build();

        this.executionKilledInput().add("unittest", executionKilled);
    }

    private static WorkerInstance workerInstance() {
        return WorkerInstance
            .builder()
            .partitions(Collections.singletonList(0))
            .workerUuid(UUID.randomUUID())
            .hostname("unit-test")
            .build();
    }

    private Execution runningAndSuccessSequential(Flow flow, int index) {
        return runningAndSuccessSequential(flow, index, State.Type.SUCCESS);
    }

    private Execution runningAndSuccessSequential(Flow flow, int index, State.Type lastState) {
        ProducerRecord<String, Execution> executionRecord;
        Task task = flow.getTasks().get(index);

        executionRecord = executionOutput().readOneRecord();

        // CREATED
        assertThat(executionRecord.value().getTaskRunList(), hasSize(index + 1));
        assertThat(executionRecord.value().getTaskRunList().get(index).getState().getCurrent(), is(State.Type.CREATED));

        // add to running queue
        TaskRun taskRun = executionRecord.value().getTaskRunList().get(index);
        WorkerTaskRunning workerTaskRunning = WorkerTaskRunning.of(
            WorkerTask.builder()
                .taskRun(taskRun)
                .task(task)
                .runContext(new RunContext())
                .build(),
            workerInstance,
            0
        );
        this.workerTaskRunningInput().add(taskRun.getId(), workerTaskRunning);

        if (lastState == State.Type.CREATED) {
            return executionRecord.value();
        }

        // RUNNING
        this.changeStatus(task, executionRecord.value().getTaskRunList().get(index), State.Type.RUNNING);

        executionRecord = executionOutput().readOneRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(index + 1));
        assertThat(executionRecord.value().getTaskRunList().get(index).getState().getCurrent(), is(State.Type.RUNNING));

        if (lastState == State.Type.RUNNING) {
            return executionRecord.value();
        }

        // SUCCESS
        this.changeStatus(task, executionRecord.value().getTaskRunList().get(index), State.Type.SUCCESS);

        executionRecord = executionOutput().readOneRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(index + 1));
        assertThat(executionRecord.value().getTaskRunList().get(index).getState().getCurrent(), is(State.Type.SUCCESS));

        return executionRecord.value();
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

    private TestInput<String, ExecutionKilled> executionKilledInput() {
        return this.testTopology
            .input(kafkaAdminService.getTopicName(ExecutionKilled.class))
            .withSerde(Serdes.String(), JsonSerde.of(ExecutionKilled.class));
    }

    private TestInput<String, WorkerTaskResult> workerTaskResultInput() {
        return this.testTopology
            .input(kafkaAdminService.getTopicName(WorkerTaskResult.class))
            .withSerde(Serdes.String(), JsonSerde.of(WorkerTaskResult.class));
    }

    private TestInput<String, WorkerTaskRunning> workerTaskRunningInput() {
        return this.testTopology
            .input(kafkaAdminService.getTopicName(WorkerTaskRunning.class))
            .withSerde(Serdes.String(), JsonSerde.of(WorkerTaskRunning.class));
    }

    private TestInput<String, WorkerInstance> workerInstanceInput() {
        return this.testTopology
            .input(kafkaAdminService.getTopicName(WorkerInstance.class))
            .withSerde(Serdes.String(), JsonSerde.of(WorkerInstance.class));
    }

    private TestOutput<String, Execution> executionOutput() {
        return this.testTopology
            .streamOutput(kafkaAdminService.getTopicName(Execution.class))
            .withSerde(Serdes.String(), JsonSerde.of(Execution.class));
    }

    private TestOutput<String, WorkerTaskRunning> workerTaskRunningOutput() {
        return this.testTopology
            .streamOutput(kafkaAdminService.getTopicName(WorkerTaskRunning.class))
            .withSerde(Serdes.String(), JsonSerde.of(WorkerTaskRunning.class));
    }

    private TestOutput<String, WorkerTask> workerTaskOutput() {
        return this.testTopology
            .streamOutput(kafkaAdminService.getTopicName(WorkerTask.class))
            .withSerde(Serdes.String(), JsonSerde.of(WorkerTask.class));
    }
}
