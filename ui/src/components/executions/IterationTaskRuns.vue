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
    import LoopIterationTree from "./LoopIterationTree.vue"
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

    // Avoids executionsStore.loadExecution, which would overwrite the shared execution.
    const executionsStore = useExecutionsStore()
    const execution = ref<Execution>()

    function isLoopTask(taskRun: {taskId: string}): boolean {
        const task = FlowUtils.findTaskById(executionsStore.flow, taskRun.taskId)
        return (task as {type?: string} | undefined)?.type === "io.kestra.plugin.core.flow.Loop"
    }

    const error = ref<unknown>(undefined)

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
