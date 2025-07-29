import {computed, ref, watch} from "vue";
import {defineStore} from "pinia";
import {useStore} from "vuex";
import {useUrlSearchParams} from "@vueuse/core"
import * as VueFlowUtils from "@kestra-io/ui-libs/vue-flow-utils"
import {Execution, useExecutionsStore} from "./executions";
import Inputs from "../utils/inputs";

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

    const taskIdToTaskRunIdMap: Record<string, string>  = {};

    async function replayOrTriggerExecution(taskId?: string, nextTasksIds?: string[], graph?: any) {
        // if all tasks prior to current task in the graph are identical
        // to the previous execution's revision,
        // we can skip them and start the execution at the current task using replayExecution()
        if (taskId && executions.value.length && graph
            && executions.value[0].graph
            && VueFlowUtils.areTasksIdenticalInGraphUntilTask(executions.value[0].graph, graph, taskId)) {
            return await executionsStore.replayExecution({
                executionId: executions.value[0].id,
                taskRunId: taskIdToTaskRunIdMap[taskId],
                breakpoints: nextTasksIds,
            });
        }

        const defaultInputValues: Record<string, any> = {}
        for (const input of (store.state.flow.flow?.inputs || [])) {
            const {type, defaults} = input;
            defaultInputValues[input.id] = Inputs.normalize(type, defaults);
        }

        return await executionsStore.triggerExecution({
            id: store.state.flow.flow?.id,
            namespace: store.state.flow.flow?.namespace,
            formData: defaultInputValues,
            kind: "PLAYGROUND",
            breakpoints: nextTasksIds,
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

    async function runUntilTask(taskId?: string) {
        await store.dispatch("flow/saveAll")

        // get the next task id to break on. If current task is provided to breakpoint,
        // the task specified by the user will not be executed.
        const {nextTasksIds, graph} = await getNextTaskIds(taskId) ?? {};

        const {data: execution} = await replayOrTriggerExecution(taskId, nextTasksIds, graph);
        executionsStore.execution = execution;

        addExecution(execution, graph);
    }

    function updateExecution(execution: ExecutionWithGraph) {
        const index = executions.value.findIndex(e => e.id === execution.id);
        if(execution.taskRunList){
            for(const taskRun of execution.taskRunList) {
                // map taskId to taskRunId for later use in replayExecution()
                taskIdToTaskRunIdMap[taskRun.taskId] = taskRun.id;
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

    return {
        enabled,
        executions,
        latestExecution: computed(() => executions.value[0]),
        clearExecutions,
        runUntilTask
    }
})