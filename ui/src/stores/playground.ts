import {computed, ref, watch} from "vue";
import {defineStore} from "pinia";
import {useStore} from "vuex";
import {Execution, useExecutionsStore} from "./executions";
import Inputs from "../utils/inputs";
import {useUrlSearchParams} from "@vueuse/core"

interface ExecutionWithGraph extends Execution {
    graph?: any;
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
    function addExecution(execution: ExecutionWithGraph, graph: any) {
        execution.graph = graph
        executions.value.unshift(execution);
    }

    function clearExecutions() {
        executions.value = [];
    }

    const store = useStore();
    const executionsStore = useExecutionsStore();

    function getRootNodes(graph: any) {
        const nodeUIDs = graph.nodes.map((node: any) => node.uid);
        const rootUIDs = nodeUIDs.filter((uid: string) => {
            return !graph.edges.some((edge: any) => edge.target === uid);
        });
        return graph.nodes.filter((node: any) => rootUIDs.includes(node.uid));
    }

    function getTargetNodes(graph: any, nodeUid?: string) {
        if (!nodeUid) {
            return undefined;
        }
        return graph.edges.filter((edge: any) => edge.source === nodeUid && edge.target);
    }

    function getNextTaskNodes(node: {uid: string}, graph: any){
        let edges:any[], nextTasks:any[], taskUid: string = node.uid;
        // loop until we find a node that is not a cluster
        do{
            // find all the edges that are connected to this task
            edges = getTargetNodes(graph, taskUid) as any[]
            // if there are no edges, return undefined
            if (edges.length === 0) {
                return [];
            }
            taskUid = edges[0].target
            nextTasks = graph.nodes.filter((node: any) => node.uid === taskUid && node.task)
        }while(!nextTasks.length)

        return nextTasks
    }

    async function getNextTaskIds(taskId?: string) {
        if (!taskId) {
            return undefined;
        }

        const graph = await store.dispatch("flow/loadGraph", {flow: store.state.flow.flow});

        // find the node uid of the task with the given taskId
        const taskNode = graph.nodes.find((node: any) => node?.task?.id === taskId);
        const nextTasksIds = getNextTaskNodes(taskNode, graph).map((node: any) => node.task.id);

        return {nextTasksIds, graph};
    }

    /**
     * Check if the tasks in the current graph are identical to the previous graph until the specified task.
     * @param previousGraph The graph from the previous execution.
     * @param currentGraph The graph from the current execution.
     * @param taskId The ID of the task to check.
     * @returns True if all tasks are identical, false otherwise.
     */
    function areTasksIdenticalInGraphUntilTask(previousGraph: any, currentGraph: any, taskId?: string) {
        if (!taskId) {
            return false;
        }

        const previousRootNodes = getRootNodes(previousGraph);
        const currentRootNodes = getRootNodes(currentGraph);

        // if the root nodes are not the same, we cannot compare
        if (previousRootNodes.length !== currentRootNodes.length) {
            return false;
        }

        let previousRootTaskNodes = previousRootNodes.flatMap((node:any) => getNextTaskNodes(node, previousGraph));
        let currentRootTaskNodes = currentRootNodes.flatMap((node:any) => getNextTaskNodes(node, currentGraph));

        // wal the graph until we find the taskId in the current root task nodes
        // or until we run out of nodes to compare
        do{
            currentRootTaskNodes = currentRootNodes.flatMap((node:any) => getNextTaskNodes(node, currentGraph));

            // stop if we find the taskId in the current root task nodes
            if(currentRootTaskNodes.some((node:any) => node.task.id === taskId)) {
                return true;
            }

            previousRootTaskNodes = previousRootNodes.flatMap((node:any) => getNextTaskNodes(node, previousGraph));

            if(previousRootTaskNodes.length !== currentRootTaskNodes.length) {
                return false;
            }

            for(const currentTaskNode of currentRootTaskNodes) {

                const prevTaskNode = previousRootTaskNodes.find((task:any) => task.id === currentTaskNode.task.id);
                // if any member of the task is different, tasks are different
                for(const key in currentTaskNode.task) {
                    if (currentTaskNode.task[key] !== prevTaskNode.task[key]) {
                        return false;
                    }
                }
            }
        }while(previousRootTaskNodes.length && currentRootTaskNodes.length);

        return true;
    }

    async function replayOrTriggerExecution(taskId?: string, nextTaskId?: string, graph?: any) {
        // if all tasks prior to current task in the graph are identical
        // to the previous execution's revision,
        // we can skip them and start the execution at the current task using replayExecution()
        if(executions.value.length && areTasksIdenticalInGraphUntilTask(executions.value[0].graph, graph, taskId)) {
            return await executionsStore.replayExecution({
                executionId: executions.value[0].id,
                taskRunId: taskId,
                breakpoints: nextTaskId ? [nextTaskId] : undefined,
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
            breakpoints: nextTaskId ? [nextTaskId] : undefined,
        })
    }

    async function runUntilTask(taskId?: string){
        await store.dispatch("flow/saveAll")

        // get the next task id to break on. If current task is provided to breakpoint,
        // the task specified by the user will not be executed.
        const {nextTasksIds, graph} = await getNextTaskIds(taskId) ?? {};
        const nextTaskId = nextTasksIds?.length ? nextTasksIds[0] : undefined;

        const {data: execution} = await replayOrTriggerExecution(taskId, nextTaskId, graph);
        executionsStore.execution = execution;

        addExecution(execution, graph);
    }

    function updateExecution(execution: Execution) {
        const index = executions.value.findIndex(e => e.id === execution.id);
        if (index !== -1) {
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
        latestExecution: computed(() => executions.value[executions.value.length - 1]),
        clearExecutions,
        runUntilTask
    }
})