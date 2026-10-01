<template>
    <div v-if="execution" class="iteration-task-runs">
        <template v-for="taskRun in execution.taskRunList ?? []" :key="taskRun.id">
            <TaskRunLine
                hideHeader
                :currentTaskRun="taskRun"
                :followedExecution="execution"
                :flow="executionsStore.flow"
                :depth="depth"
                :selectedAttemptNumberByTaskRunId="{[taskRun.id]: 0}"
            />
            <LoopIterationTree
                v-if="isLoopTask(taskRun)"
                :key="`${execution.id}-${taskRun.taskId}`"
                :executionId="execution.id"
                :taskId="taskRun.taskId"
                :namespace="namespace"
                :flowId="flowId"
                :depth="depth + 1"
            />
        </template>
    </div>
    <KsAlert v-else-if="error" type="error">
        {{ $t("loop_iterations_load_error") }}
        <KsButton size="small" link @click="retryFetch">{{ $t("retry") }}</KsButton>
    </KsAlert>
    <div v-else class="loop-loading">
        <KsIcon class="is-loading" :size="20">
            <Loading />
        </KsIcon>
    </div>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue"
    import TaskRunLine from "./TaskRunLine.vue"
    import {useExecutionsStore, type Execution} from "../../stores/executions"
    import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"
    import {KsAlert, KsButton, KsIcon} from "@kestra-io/design-system"
    import Loading from "vue-material-design-icons/Loading.vue"
    import * as FlowUtils from "../../utils/flowUtils"

    const props = defineProps<{
        executionId: string;
        namespace: string;
        flowId: string;
        depth: number;
    }>()

    // `executionsStore.loadExecution` commits to the store's single shared `execution` ref —
    // the same one the whole Gantt page renders from. Calling it here for one iteration would
    // silently replace the entire page's data with that iteration's, cascading further at each
    // nested Loop. So this fetches straight from the API instead, keeping the result local.
    const executionsStore = useExecutionsStore()
    const execution = ref<Execution>()

    // Read fresh from the store on every call, exactly like Gantt.vue's own equivalent check
    // (taskTypeByTaskRunId), rather than caching `executionsStore.flow` once at setup — a stale
    // or not-yet-populated snapshot here fails silently (no thrown error) rather than crashing,
    // since findTaskById tolerates an undefined flow, so nesting would just quietly never appear.
    function isLoopTask(taskRun: {taskId: string}): boolean {
        const task = FlowUtils.findTaskById(executionsStore.flow, taskRun.taskId)
        return (task as {type?: string} | undefined)?.type === "io.kestra.plugin.core.flow.Loop"
    }

    const error = ref<unknown>(undefined)

    // Reacts to executionId rather than fetching once on mount, so that if this component
    // instance is ever reused for a different iteration (the exact bug class fixed by the
    // :key additions on LoopIterationTree above), it refetches instead of showing stale data.
    // The counter guards against the same instance's *own* out-of-order responses: a slow
    // fetch for an executionId this component has since moved on from must not land on top
    // of a newer, already-resolved one — the same pattern executionsStore.loadExecution uses.
    let latestFetch = 0
    async function fetchExecution(id: string) {
        const fetch = ++latestFetch
        error.value = undefined
        execution.value = undefined
        try {
            const data = await ExecutionsAPI.execution({executionId: id}) as unknown as Execution
            if (fetch === latestFetch) execution.value = data
        } catch (e) {
            if (fetch === latestFetch) error.value = e
        }
    }
    watch(() => props.executionId, (id) => fetchExecution(id), {immediate: true})

    function retryFetch() {        
        fetchExecution(props.executionId)
    }
</script>

<style scoped lang="scss">
    .iteration-task-runs {
        display: flex;
        flex-direction: column;
    }
</style>
