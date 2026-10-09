<template>
    <TaskRunDetails
        v-if="!loading"
        :key="childExecutionId || taskRun.id"
        :targetExecutionId="childExecutionId"
        :taskRunId="childExecutionId ? undefined : taskRun.id"
        :targetFlow="childExecutionId ? undefined : flow"
        :levelFilter="levelFilter"
        :hideTaskHeader="!childExecutionId"
        :excludeMetas="['namespace', 'flowId', 'taskId', 'executionId']"
    />
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import type {FlowForExecution} from "@kestra-io/kestra-sdk"
    import type {LevelFilterValue} from "@kestra-io/design-system"
    import type {Execution} from "../../stores/executions"
    import {loadTaskRunOutputs} from "../../composables/useTaskRunOutputs"
    import TaskRunDetails from "../logs/TaskRunDetails.vue"

    const props = defineProps<{
        execution: Execution
        taskRun: {id: string; state: {current: string}; outputs?: Record<string, unknown>}
        taskType?: string
        flow: FlowForExecution
        levelFilter?: LevelFilterValue
    }>()

    const outputs = ref<Record<string, unknown>>({})
    const loading = ref(false)
    const childExecutionId = computed(() => {
        const id = outputs.value.executionId ?? props.taskRun.outputs?.executionId
        return typeof id === "string" ? id : undefined
    })

    watch(
        [() => props.execution.id, () => props.taskRun.id, () => props.taskRun.state.current, () => props.taskType],
        async ([executionId, taskRunId], previous, onCleanup) => {
            if (executionId !== previous?.[0] || taskRunId !== previous?.[1]) outputs.value = {}
            loading.value = false
            if (props.taskType !== "io.kestra.plugin.core.flow.Subflow" || typeof props.taskRun.outputs?.executionId === "string") return

            let cancelled = false
            onCleanup(() => { cancelled = true })
            loading.value = true
            try {
                const values = await loadTaskRunOutputs(executionId, taskRunId)
                if (!cancelled) outputs.value = values
            } finally {
                if (!cancelled) loading.value = false
            }
        },
        {immediate: true},
    )
</script>
