package org.kestra.runner.kafka;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.test.TestRecord;
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
import org.kestra.core.serializers.JacksonMapper;
import org.kestra.core.tasks.flows.Parallel;
import org.kestra.core.utils.IdUtils;
import org.kestra.core.utils.TestsUtils;
import org.kestra.runner.kafka.configs.ClientConfig;
import org.kestra.runner.kafka.serializers.JsonSerde;
import org.kestra.runner.kafka.services.KafkaAdminService;
import org.kestra.runner.kafka.services.KafkaStreamSourceService;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@SuppressWarnings("resource")
@MicronautTest
@Slf4j
class KafkaExecutorTest {
    @Inject
    ApplicationContext applicationContext;

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

    TopologyTestDriver testTopology;

    static WorkerInstance workerInstance = workerInstance();

    @BeforeEach
    void init() throws IOException, URISyntaxException {
        TestsUtils.loads(repositoryLoader);

        Properties properties = new Properties();
        properties.putAll(clientConfig.getProperties());
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "unit-test");
        properties.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-stream-unit/" + UUID.randomUUID());

        testTopology = new TopologyTestDriver(stream.topology(), properties);

        applicationContext.registerSingleton(new KafkaTemplateExecutor(
            testTopology.getKeyValueStore("template")
        ));
    }

    @AfterEach
    void tear() {
        if (this.testTopology != null) {
            testTopology.close();
        }
    }

    @Test
    void standard() {
        Flow flow = flowRepository.findById("org.kestra.tests", "logs").orElseThrow();
        this.flowInput().pipeInput(flow.uid(), flow);

        createExecution(flow);

        // task
        runningAndSuccessSequential(flow, 0);
        runningAndSuccessSequential(flow, 1);
        runningAndSuccessSequential(flow, 2);

        TestRecord<String, Execution> executionRecord = executionOutput().readRecord();

        assertThat(executionRecord.value().getState().getCurrent(), is(State.Type.SUCCESS));

        // running most be deleted at the end
        assertThat(workerTaskRunningOutput().readRecord().value(), is(nullValue()));
        assertThat(workerTaskRunningOutput().readRecord().value(), is(nullValue()));
        assertThat(workerTaskRunningOutput().readRecord().value(), is(nullValue()));
        assertThat(workerTaskRunningOutput().isEmpty(), is(true));
    }

    @Test
    void concurrent() {
        Flow flow = flowRepository.findById("org.kestra.tests", "logs").orElseThrow();
        this.flowInput().pipeInput(flow.uid(), flow);

        createExecution(flow);

        // task
        runningAndSuccessSequential(flow, 0);
        runningAndSuccessSequential(flow, 1);

        // next
        TestRecord<String, Execution> executionRecord = executionOutput().readRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(3));
        assertThat(executionRecord.value().getTaskRunList().get(2).getState().getCurrent(), is(State.Type.CREATED));


        Task task = flow.getTasks().get(2);
        TaskRun taskRun = executionRecord.value().getTaskRunList().get(2);

        // concurrent
        this.changeStatus(task, taskRun, State.Type.RUNNING);
        this.changeStatus(task, taskRun, State.Type.SUCCESS);

        executionRecord = executionOutput().readRecord();
        executionRecord = executionOutput().readRecord();
        executionRecord = executionOutput().readRecord();

        assertThat(executionRecord.value().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    void killed() {
        Flow flow = flowRepository.findById("org.kestra.tests", "logs").orElseThrow();
        this.flowInput().pipeInput(flow.uid(), flow);

        createExecution(flow);

        // task
        Execution execution = runningAndSuccessSequential(flow, 0);

        // multiple killed should have no impact
        createKilled(execution);
        createKilled(execution);
        createKilled(execution);

        // next
        TestRecord<String, Execution> executionRecord = executionOutput().readRecord();
        executionRecord = executionOutput().readRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(2));
        assertThat(executionRecord.value().getState().getCurrent(), is(State.Type.KILLING));

        Task task = flow.getTasks().get(1);
        TaskRun taskRun = executionRecord.value().getTaskRunList().get(1);

        // late arrival from worker
        this.changeStatus(task, taskRun, State.Type.RUNNING);
        this.changeStatus(task, taskRun, State.Type.SUCCESS);

        executionRecord = executionOutput().readRecord();
        executionRecord = executionOutput().readRecord();
        executionRecord = executionOutput().readRecord();

        assertThat(executionRecord.value().getTaskRunList(), hasSize(2));
        assertThat(executionRecord.value().getTaskRunList().get(1).getState().getCurrent(), is(State.Type.SUCCESS));

        assertThat(executionRecord.value().getState().getCurrent(), is(State.Type.KILLED));
        assertThat(executionOutput().isEmpty(), is(true));
    }

    @Test
    void killedAlreadyFinished() {
        Flow flow = flowRepository.findById("org.kestra.tests", "logs").orElseThrow();
        this.flowInput().pipeInput(flow.uid(), flow);

        createExecution(flow);

        // task
        runningAndSuccessSequential(flow, 0);
        runningAndSuccessSequential(flow, 1);
        Execution execution = runningAndSuccessSequential(flow, 2);

        createKilled(execution);

        // next
        TestRecord<String, Execution> executionRecord = executionOutput().readRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(3));
        assertThat(executionRecord.value().getState().getCurrent(), is(State.Type.SUCCESS));

        assertThat(executionOutput().isEmpty(), is(true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void killedParallel(boolean killed) {
        Flow flow = flowRepository.findById("org.kestra.tests", "parallel").orElseThrow();
        this.flowInput().pipeInput(flow.uid(), flow);

        createExecution(flow);
        Parallel parent = (Parallel) flow.getTasks().get(0);
        Task firstChild = parent.getTasks().get(0);
        Task secondChild = parent.getTasks().get(1);

        // parent > worker > RUNNING
        Execution executionRecord = executionOutput().readRecord().value();
        assertThat(executionRecord.getTaskRunList(), hasSize(1));
        this.changeStatus(parent, executionRecord.getTaskRunList().get(0), State.Type.RUNNING);

        // parent > execution RUNNING
        executionRecord = executionOutput().readRecord().value();
        assertThat(executionRecord.getTaskRunList(), hasSize(1));
        assertThat(executionRecord.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.RUNNING));


        // first child > RUNNING
        executionRecord = executionOutput().readRecord().value();
        assertThat(executionRecord.getTaskRunList(), hasSize(7));
        assertThat(executionRecord.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.CREATED));

        this.changeStatus(firstChild, executionRecord.getTaskRunList().get(1), State.Type.RUNNING);
        executionRecord = executionOutput().readRecord().value();
        assertThat(executionRecord.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(executionRecord.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.CREATED));

        // killed execution
        createKilled(executionRecord);
        executionRecord = executionOutput().readRecord().value();
        executionRecord = executionOutput().readRecord().value();
        assertThat(executionRecord.getState().getCurrent(), is(State.Type.KILLING));


        // killed all the creation and killing the parent
        for (int i = 0; i < 14; i++) {
            executionRecord = executionOutput().readRecord().value();
        }

        // can't catch parent killing here
        // assertThat(executionRecord.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.KILLING));

        for (int i = 2; i < 5; i++) {
            assertThat(executionRecord.getTaskRunList().get(i).getState().getCurrent(), is(State.Type.KILLED));
        }

        // kill the first child
        if (killed) {
            this.changeStatus(firstChild, executionRecord.getTaskRunList().get(1), State.Type.KILLED);
        } else {
            this.changeStatus(firstChild, executionRecord.getTaskRunList().get(1), State.Type.SUCCESS);
        }

        // killing state
        for (int i = 0; i < 4; i++) {
            executionRecord = executionOutput().readRecord().value();
        }

        // control
        assertThat(executionRecord.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.KILLED));
        assertThat(executionRecord.getTaskRunList().get(1).getState().getCurrent(), is(killed ? State.Type.KILLED : State.Type.SUCCESS));
        assertThat(executionRecord.getState().getCurrent(), is(State.Type.KILLED));

        assertThat(executionOutput().isEmpty(), is(true));
    }

    @Test
    void parallel() {
        Flow flow = flowRepository.findById("org.kestra.tests", "parallel").orElseThrow();
        this.flowInput().pipeInput(flow.uid(), flow);

        createExecution(flow);
        Parallel parent = (Parallel) flow.getTasks().get(0);
        Task last = flow.getTasks().get(1);

        // parent > worker > RUNNING
        TestRecord<String, Execution> executionRecord = executionOutput().readRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(1));
        this.changeStatus(parent, executionRecord.value().getTaskRunList().get(0), State.Type.RUNNING);

        // parent > execution RUNNING
        executionRecord = executionOutput().readRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(1));
        assertThat(executionRecord.value().getTaskRunList().get(0).getState().getCurrent(), is(State.Type.RUNNING));


        executionRecord = executionOutput().readRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(7));

        // Task > all RUNNING
        for (ListIterator<Task> it = parent.getTasks().listIterator(); it.hasNext(); ) {
            int index = it.nextIndex();
            Task next = it.next();

            // Task > CREATED
            assertThat(executionRecord.value().getTaskRunList().get(index + 1).getState().getCurrent(), is(State.Type.CREATED));

            // Task > RUNNING
            this.changeStatus(next, executionRecord.value().getTaskRunList().get(index + 1), State.Type.RUNNING);

            executionRecord = executionOutput().readRecord();
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
            executionRecord = executionOutput().readRecord();
            assertThat(executionRecord.value().getTaskRunList().get(index + 1).getState().getCurrent(), is(State.Type.SUCCESS));
        }

        // parent terminated
        executionRecord = executionOutput().readRecord();
        assertThat(executionRecord.value().getTaskRunList().get(0).getState().getCurrent(), is(State.Type.SUCCESS));

        // last
        executionRecord = executionOutput().readRecord();
        this.changeStatus(last, executionRecord.value().getTaskRunList().get(7), State.Type.RUNNING);

        this.changeStatus(last, executionRecord.value().getTaskRunList().get(7), State.Type.RUNNING);
        executionRecord = executionOutput().readRecord();
        assertThat(executionRecord.value().getTaskRunList().get(7).getState().getCurrent(), is(State.Type.RUNNING));

        this.changeStatus(last, executionRecord.value().getTaskRunList().get(7), State.Type.SUCCESS);
        executionRecord = executionOutput().readRecord();
        assertThat(executionRecord.value().getTaskRunList().get(7).getState().getCurrent(), is(State.Type.SUCCESS));

        // ok
        executionRecord = executionOutput().readRecord();
        assertThat(executionRecord.value().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    void flowTrigger() {
        Flow triggerFlow = flowRepository.findById("org.kestra.tests", "trigger-flow-listener-no-inputs").orElseThrow();
        this.flowInput().pipeInput(triggerFlow.uid(), triggerFlow);

        // we add 2 version of the same triggering flow to be sure to have only the last one triggered
        Flow updateTriggerFlow = triggerFlow.withRevision(2);
        this.flowInput().pipeInput(updateTriggerFlow.uid(), updateTriggerFlow.withRevision(2));

        Flow firstFlow = flowRepository.findById("org.kestra.tests", "trigger-flow").orElseThrow();
        this.flowInput().pipeInput(firstFlow.uid(), firstFlow);

        createExecution(firstFlow);

        // task
        runningAndSuccessSequential(firstFlow, 0);

        TestRecord<String, Execution> firstExecution = executionOutput().readRecord();
        assertThat(firstExecution.value().getState().getCurrent(), is(State.Type.SUCCESS));

        TestRecord<String, Execution> triggerExecution = executionOutput().readRecord();
        assertThat(triggerExecution.value().getState().getCurrent(), is(State.Type.CREATED));

        runningAndSuccessSequential(triggerFlow, 0);

        triggerExecution = executionOutput().readRecord();
        assertThat(triggerExecution.value().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    void multipleTrigger() {
        Flow flowA = flowRepository.findById("org.kestra.tests", "trigger-multiplecondition-flow-a").orElseThrow();
        this.flowInput().pipeInput(flowA.uid(), flowA);

        Flow flowB = flowRepository.findById("org.kestra.tests", "trigger-multiplecondition-flow-b").orElseThrow();
        this.flowInput().pipeInput(flowB.uid(), flowB);

        Flow triggerFlow = flowRepository.findById("org.kestra.tests", "trigger-multiplecondition-listener").orElseThrow();
        this.flowInput().pipeInput(triggerFlow.uid(), triggerFlow);

        // first
        createExecution(flowA);
        runningAndSuccessSequential(flowA, 0);

        TestRecord<String, Execution> executionA = executionOutput().readRecord();
        assertThat(executionA.value().getState().getCurrent(), is(State.Type.SUCCESS));

        // second
        createExecution(flowB);
        runningAndSuccessSequential(flowB, 0);

        TestRecord<String, Execution> executionB = executionOutput().readRecord();
        assertThat(executionB.value().getState().getCurrent(), is(State.Type.SUCCESS));

        // trigger start
        TestRecord<String, Execution> triggerExecution = executionOutput().readRecord();
        assertThat(triggerExecution.value().getState().getCurrent(), is(State.Type.CREATED));

        runningAndSuccessSequential(triggerFlow, 0);

        triggerExecution = executionOutput().readRecord();
        assertThat(triggerExecution.value().getState().getCurrent(), is(State.Type.SUCCESS));
    }


    @Test
    void workerRebalanced() {
        Flow flow = flowRepository.findById("org.kestra.tests", "logs").orElseThrow();
        this.flowInput().pipeInput(flow.uid(), flow);
        this.workerInstanceInput().pipeInput(workerInstance.getWorkerUuid().toString(), workerInstance);

        createExecution(flow);

        Execution execution = runningAndSuccessSequential(flow, 0, State.Type.RUNNING);
        String taskRunId = execution.getTaskRunList().get(0).getId();

        assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(this.workerTaskOutput().readRecord().value().getTaskRun().getState().getCurrent(), is(State.Type.CREATED));

        // declare a new worker instance
        WorkerInstance newInstance = workerInstance();
        this.workerInstanceInput().pipeInput(newInstance.getWorkerUuid().toString(), newInstance);

        // receive a new WorkTask meaning that the resend is done
        TestRecord<String, WorkerTask> workerTaskRecord = this.workerTaskOutput().readRecord();
        assertThat(workerTaskRecord.value().getTaskRun().getState().getCurrent(), is(State.Type.CREATED));
        assertThat(workerTaskRecord.value().getTaskRun().getId(), is(taskRunId));

        // running is deleted
        TestRecord<String, WorkerTaskRunning> workerTaskRunningRecord = workerTaskRunningOutput().readRecord();
        assertThat(workerTaskRunningRecord.value(), is(nullValue()));
        assertThat(workerTaskRunningRecord.key(), is(taskRunId));
    }

    private void createExecution(Flow flow) {
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .state(new State())
            .build();

        this.executionInput().pipeInput(execution.getId(), execution);
    }

    private void createKilled(Execution execution) {
        ExecutionKilled executionKilled = ExecutionKilled.builder()
            .executionId(execution.getId())
            .build();

        this.executionKilledInput().pipeInput(executionKilled.getExecutionId(), executionKilled);
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
        TestRecord<String, Execution> executionRecord;
        Task task = flow.getTasks().get(index);

        executionRecord = executionOutput().readRecord();

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
        this.workerTaskRunningInput().pipeInput(taskRun.getId(), workerTaskRunning);

        if (lastState == State.Type.CREATED) {
            return executionRecord.value();
        }

        // RUNNING
        this.changeStatus(task, executionRecord.value().getTaskRunList().get(index), State.Type.RUNNING);

        executionRecord = executionOutput().readRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(index + 1));
        assertThat(executionRecord.value().getTaskRunList().get(index).getState().getCurrent(), is(State.Type.RUNNING));

        if (lastState == State.Type.RUNNING) {
            return executionRecord.value();
        }

        // SUCCESS
        this.changeStatus(task, executionRecord.value().getTaskRunList().get(index), State.Type.SUCCESS);

        executionRecord = executionOutput().readRecord();
        assertThat(executionRecord.value().getTaskRunList(), hasSize(index + 1));
        assertThat(executionRecord.value().getTaskRunList().get(index).getState().getCurrent(), is(State.Type.SUCCESS));

        return executionRecord.value();
    }

    private void changeStatus(Task task, TaskRun taskRun, State.Type state) {
        this.workerTaskResultInput()
            .pipeInput("unittest", WorkerTaskResult.builder()
                .task(task)
                .taskRun(taskRun.withState(state))
                .build()
            );

    }

    private TestInputTopic<String, Flow> flowInput() {
        return this.testTopology
            .createInputTopic(
                kafkaAdminService.getTopicName(Flow.class),
                Serdes.String().serializer(),
                JsonSerde.of(Flow.class).serializer()
            );
    }

    private TestInputTopic<String, Execution> executionInput() {
        return this.testTopology
            .createInputTopic(
                kafkaAdminService.getTopicName(Execution.class),
                Serdes.String().serializer(),
                JsonSerde.of(Execution.class).serializer()
            );
    }

    private TestInputTopic<String, ExecutionKilled> executionKilledInput() {
        return this.testTopology
            .createInputTopic(
                kafkaAdminService.getTopicName(ExecutionKilled.class),
                Serdes.String().serializer(),
                JsonSerde.of(ExecutionKilled.class).serializer()
            );
    }

    private TestInputTopic<String, WorkerTaskResult> workerTaskResultInput() {
        return this.testTopology
            .createInputTopic(
                kafkaAdminService.getTopicName(WorkerTaskResult.class),
                Serdes.String().serializer(),
                JsonSerde.of(WorkerTaskResult.class).serializer()
            );
    }

    private TestInputTopic<String, WorkerTaskRunning> workerTaskRunningInput() {
        return this.testTopology
            .createInputTopic(
                kafkaAdminService.getTopicName(WorkerTaskRunning.class),
                Serdes.String().serializer(),
                JsonSerde.of(WorkerTaskRunning.class).serializer()
            );
    }

    private TestInputTopic<String, WorkerInstance> workerInstanceInput() {
        return this.testTopology
            .createInputTopic(
                kafkaAdminService.getTopicName(WorkerInstance.class),
                Serdes.String().serializer(),
                JsonSerde.of(WorkerInstance.class).serializer()
            );
    }

    private TestOutputTopic<String, Execution> executionOutput() {
        return this.testTopology
            .createOutputTopic(
                kafkaAdminService.getTopicName(Execution.class),
                Serdes.String().deserializer(),
                JsonSerde.of(Execution.class).deserializer()
            );
    }

    private TestOutputTopic<String, WorkerTaskRunning> workerTaskRunningOutput() {
        return this.testTopology
            .createOutputTopic(
                kafkaAdminService.getTopicName(WorkerTaskRunning.class),
                Serdes.String().deserializer(),
                JsonSerde.of(WorkerTaskRunning.class).deserializer()
            );
    }

    private TestOutputTopic<String, WorkerTask> workerTaskOutput() {
        return this.testTopology
            .createOutputTopic(
                kafkaAdminService.getTopicName(WorkerTask.class),
                Serdes.String().deserializer(),
                JsonSerde.of(WorkerTask.class).deserializer()
            );
    }
}
