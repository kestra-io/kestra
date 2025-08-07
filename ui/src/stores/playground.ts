import {computed, ref, watch} from "vue";
import {defineStore} from "pinia";
import {useStore} from "vuex";
import {useUrlSearchParams} from "@vueuse/core"
import * as VueFlowUtils from "@kestra-io/ui-libs/vue-flow-utils"
import {Execution, useExecutionsStore} from "./executions";
import Inputs from "../utils/inputs";
import {useRoute, useRouter} from "vue-router";
import {State} from "@kestra-io/ui-libs";
import {useToast} from "../utils/toast";
import {useI18n} from "vue-i18n";

interface ExecutionWithGraph extends Execution {
    graph?: VueFlowUtils.FlowGraph;
}

export const usePlaygroundStore = defineStore("playground", () => {
    const params = useUrlSearchParams("history", {
        removeFalsyValues: true
    })

    const enabled = ref<boolean>(params.playground === "on" && localStorage.getItem("editorPlayground") === "true");
    watch(enabled, (newValue) => {
        if (newValue) {
            params.playground = "on"
        } else {
            params.playground = ""
        }
    })

    const route = useRoute();
    const router = useRouter();

    function navigateToEdit(runUntilTaskId?: string, runDownstreamTasks?: boolean) {
        const flowParsed = store.state.flow.flow;
        router.push({
            name: "flows/update",
            params: {
                id: flowParsed.id,
                namespace: flowParsed.namespace,
                tab: "edit",
                tenant: route.params.tenant,
            },
            query: {
                playground: "on",
                runUntilTaskId,
                runDownstreamTasks: runDownstreamTasks ? "true" : undefined,
            }
        });
    }

    const executions = ref<ExecutionWithGraph[]>([])
    function addExecution(execution: ExecutionWithGraph, graph: VueFlowUtils.FlowGraph) {
        execution.graph = graph
        executions.value.unshift(execution);
    }

    function clearExecutions() {
        executions.value = [];
        executionsStore.execution = undefined;
    }

    const store = useStore();
    const executionsStore = useExecutionsStore();

    const taskIdToTaskRunIdMap: Map<string, string>  = new Map();

    async function replayOrTriggerExecution(taskId?: string, breakpoints?: string[], graph?: any) {
        // if all tasks prior to current task in the graph are identical
        // to the previous execution's revision,
        // we can skip them and start the execution at the current task using replayExecution()
        if (taskId && executions.value.length && graph
            && executions.value[0].graph
            && VueFlowUtils.areTasksIdenticalInGraphUntilTask(executions.value[0].graph, graph, taskId)
            && taskIdToTaskRunIdMap.has(taskId)) {
            return await executionsStore.replayExecution({
                executionId: executions.value[0].id,
                taskRunId: taskIdToTaskRunIdMap.get(taskId),
                revision: store.state.flow.flow.revision,
                breakpoints,
            });
        }

        const defaultInputValues: Record<string, any> = {}
        for (const input of (store.state.flow.flow?.inputs || [])) {
            const {type, defaults} = input;
            // for dates, no need to normalize the value
            // https://github.com/kestra-io/kestra/issues/10576
            defaultInputValues[input.id] = type === "DATE"
                ? defaults
                : Inputs.normalize(type, defaults);
        }

        return await executionsStore.triggerExecution({
            id: store.state.flow.flow?.id,
            namespace: store.state.flow.flow?.namespace,
            formData: defaultInputValues,
            kind: "PLAYGROUND",
            breakpoints,
        })
    }

    async function getNextTaskIds(taskId?: string) {
        const graph = await store.dispatch("flow/loadGraph", {flow: store.state.flow.flow});

        if (!taskId) {
            return {nextTasksIds: [], graph};
        }

        // find the node uid of the task with the given taskId
        const taskNode = graph.nodes.find((node: any) => node?.task?.id === taskId);

        const nextTasksNodes = VueFlowUtils.getNextTaskNodes(graph, taskNode);

        const nextTasksIds = nextTasksNodes.map((node: any) => node.task.id);

        return {nextTasksIds, graph};
    }

    const latestExecution = computed(() => executions.value[0]);

    const nonFinalStates = [
        State.KILLING,
        State.RUNNING,
        State.RESTARTED,
        State.CREATED,
    ]

    const executionState = computed(() => {
        return latestExecution.value?.state.current;
    })

    const readyToStartPure = computed(() => {
        return !latestExecution.value || !nonFinalStates.includes(executionState.value)
    })

    const readyToStart = ref(readyToStartPure.value);
    watch(readyToStartPure, (newValue) => {
        if(newValue) {
            setTimeout(() => {
                readyToStart.value = newValue;
            }, 1000);
        } else {
            readyToStart.value = newValue
        }
    });

    const toast = useToast();

    function runFromQuery(){
        if(route.query.runUntilTaskId) {
            const {runUntilTaskId, runDownstreamTasks} = route.query;
            runUntilTask(runUntilTaskId.toString(), Boolean(runDownstreamTasks));

            // remove the query parameters to avoid running the same task again
            router.replace({
                name: route.name,
                params: route.params,
                query: {
                    ...route.query,
                    runUntilTaskId: undefined,
                    runDownstreamTasks: undefined,  // remove the query parameter
                }
            });
        }
    }

    const {t} = useI18n();

    async function runUntilTask(taskId?: string, runDownstreamTasks = false) {
        if(readyToStart.value === false) {
            console.warn("Playground is not ready to start, latest execution is still in progress");
            return
        }

        readyToStart.value = false;

        if(store.state.flow.isCreating){
            toast.confirm(
                t("playground.confirm_create"),
                async () => {
                    await store.dispatch("flow/saveAll");
                    navigateToEdit(taskId, runDownstreamTasks);
                }
            );
            return;
        }

        await store.dispatch("flow/saveAll")
        // get the next task id to break on. If current task is provided to breakpoint,
        // the task specified by the user will not be executed.
        const {nextTasksIds, graph} = await getNextTaskIds(runDownstreamTasks ? undefined : taskId) ?? {};

        const {data: execution} = await replayOrTriggerExecution(taskId, runDownstreamTasks ? undefined : nextTasksIds, graph);

        // don't keep taskRunIds from previous executions
        // because of https://github.com/kestra-io/kestra/issues/10462
        taskIdToTaskRunIdMap.clear();

        executionsStore.execution = execution;

        addExecution(execution, graph);
    }

    function updateExecution(execution: ExecutionWithGraph) {
        const index = executions.value.findIndex(e => e.id === execution.id);
        if(execution.taskRunList){
            for(const taskRun of execution.taskRunList) {
                // map taskId to taskRunId for later use in replayExecution()
                taskIdToTaskRunIdMap.set(taskRun.taskId, taskRun.id);
            }
        }
        if (index !== -1) {
            const graph = executions.value[index].graph;
            execution.graph = graph; // keep the graph reference
            executions.value[index] = execution;
        }
    }

    // when following an execution, the status changes after creation
    watch(() => executionsStore.execution, (newValue) => {
        if (newValue) {
            updateExecution(newValue);
        }
    })

    const dropdownOpened = ref<boolean>(false);

    return {
        enabled,
        dropdownOpened,
        readyToStart,
        executions,
        latestExecution,
        clearExecutions,
        runUntilTask,
        runFromQuery,
        executionState
    }
})
