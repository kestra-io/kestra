import {computed, ref, watch} from "vue";
import {defineStore} from "pinia";
import {useStore} from "vuex";
import {Execution, useExecutionsStore} from "./executions";
import Inputs from "../utils/inputs";
import {useUrlSearchParams} from "@vueuse/core"

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

    const executions = ref<Execution[]>([])
    function addExecution(execution: Execution) {
        executions.value.unshift(execution);
    }

    function clearExecutions() {
        executions.value = [];
    }

    const store = useStore();
    const executionsStore = useExecutionsStore();

    function getTargetNodes(graph: any, nodeUid?: string) {
        if (!nodeUid) {
            return undefined;
        }
        return graph.edges.filter((edge: any) => edge.source === nodeUid && edge.target);
    }

    async function getNextTaskId(taskId?: string) {
        if (!taskId) {
            return undefined;
        }
        const flow = store.state.flow.flow;
        if (!flow) {
            return undefined;
        }

        const graph = await store.dispatch("flow/loadGraph", {flow: store.state.flow.flow});

        // find the node uid of the task with the given taskId
        let taskUid = graph.nodes.find((node: any) => node?.task?.id === taskId).uid;

        let edges:any[], nextTask:any;
        // loop until we find a node that is not a cluster
        do{
            // find all the edges that are connected to this task
            edges = getTargetNodes(graph, taskUid) as any[]
            // if there are no edges, return undefined
            if (edges.length === 0) {
                return undefined;
            }
            taskUid = edges[0].target
            nextTask = graph.nodes.find((node: any) => node.uid === taskUid && node.task)
        }while(!nextTask)

        return nextTask?.task.id;
    }

    async function runUntilTask(taskId?: string){
        await store.dispatch("flow/saveAll")

        const defaultInputValues: Record<string, any> = {}
        for (const input of (store.state.flow.flow?.inputs || [])) {
            const {type, defaults} = input;
            defaultInputValues[input.id] = Inputs.normalize(type, defaults);
        }

        // get the next task id to break on. If current task is provided to breakpoint,
        // the task specified by the user will not be executed.
        const nextTaskId = await getNextTaskId(taskId);

        const {data: execution} = await executionsStore.triggerExecution({
            id: store.state.flow.flow?.id,
            namespace: store.state.flow.flow?.namespace,
            formData: defaultInputValues,
            kind: "PLAYGROUND",
            breakpoints: nextTaskId ? [nextTaskId] : undefined,
        })
        executionsStore.execution = execution;

        addExecution(execution);
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
        addExecution,
        clearExecutions,
        runUntilTask
    }
})