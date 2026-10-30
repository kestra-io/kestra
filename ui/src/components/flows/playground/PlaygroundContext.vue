<template>
    <div class="playground-context">
        <KsEmpty v-if="!taskRuns.length" :description="$t('playground.context.empty')" :background="false" />
        <template v-else>
            <KsSelect
                v-model="selectedTaskRunId"
                :aria-label="$t('playground.context.task_run')"
                class="task-run-select"
                data-test="playground-context-task-run"
            >
                <KsOption
                    v-for="taskRun in taskRuns"
                    :key="taskRun.id"
                    :value="taskRun.id"
                    :label="taskRun.value ? `${taskRun.taskId} (${taskRun.value})` : taskRun.taskId"
                />
            </KsSelect>
            <KsAlert v-if="failed" type="error" :title="$t('playground.context.error')" :closable="false" />
            <div v-else v-ks-loading="loading" class="context-tree" data-test="playground-context-tree">
                <KsJsonTree v-if="context" :value="context" />
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {KsAlert, KsEmpty, KsJsonTree, KsOption, KsSelect, vKsLoading} from "@kestra-io/design-system"
    import * as PlaygroundAPI from "@kestra-io/kestra-sdk/playground"
    import {useExecutionsStore} from "../../../stores/executions"

    const executionsStore = useExecutionsStore()

    const execution = computed(() => executionsStore.execution)
    const taskRuns = computed(() => execution.value?.taskRunList ?? [])

    /** The task the run stopped before is the one whose context a debugging user wants first. */
    const defaultTaskRunId = computed(() => {
        const stopped = taskRuns.value.find(taskRun => taskRun.state.current === "BREAKPOINT")
        return (stopped ?? taskRuns.value[taskRuns.value.length - 1])?.id
    })

    const selectedTaskRunId = ref<string>()
    watch(() => execution.value?.id, () => {
        selectedTaskRunId.value = defaultTaskRunId.value
    }, {immediate: true})
    watch(defaultTaskRunId, (taskRunId) => {
        if (!selectedTaskRunId.value || !taskRuns.value.some(taskRun => taskRun.id === selectedTaskRunId.value)) {
            selectedTaskRunId.value = taskRunId
        }
    })

    const context = ref<Record<string, unknown>>()
    const loading = ref(false)
    const failed = ref(false)
    let request = 0

    async function loadContext() {
        const current = execution.value
        const taskRunId = selectedTaskRunId.value
        context.value = undefined
        failed.value = false
        if (!current || !taskRunId) return

        const sequence = ++request
        loading.value = true
        try {
            const response = await PlaygroundAPI.taskRunContext({namespace: current.namespace, id: current.flowId, executionId: current.id, taskRunId})
            if (sequence === request) context.value = response.variables ?? {}
        } catch {
            if (sequence === request) failed.value = true
        } finally {
            if (sequence === request) loading.value = false
        }
    }

    watch(
        () => [selectedTaskRunId.value, execution.value?.state.current, taskRuns.value.find(taskRun => taskRun.id === selectedTaskRunId.value)?.state.current],
        loadContext,
        {immediate: true},
    )
</script>

<style scoped>
    .playground-context {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
    }

    .task-run-select {
        max-width: 20rem;
    }

    .context-tree {
        min-height: 4rem;
    }
</style>
