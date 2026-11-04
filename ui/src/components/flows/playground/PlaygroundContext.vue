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
            <KsAlert v-if="playgroundStore.contextLoadFailed" type="error" :title="$t('playground.context.error')" :closable="false" />
            <div v-else v-ks-loading="playgroundStore.contextLoading" class="context-tree" data-test="playground-context-tree">
                <KsJsonTree v-if="playgroundStore.context" :value="playgroundStore.context" />
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {KsAlert, KsEmpty, KsJsonTree, KsOption, KsSelect, vKsLoading} from "@kestra-io/design-system"
    import {useExecutionsStore} from "../../../stores/executions"
    import {usePlaygroundStore} from "../../../stores/playground"

    const executionsStore = useExecutionsStore()
    const playgroundStore = usePlaygroundStore()

    const execution = computed(() => executionsStore.execution)
    const taskRuns = computed(() => execution.value?.taskRunList ?? [])

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

    watch(
        () => [selectedTaskRunId.value, execution.value?.state.current, taskRuns.value.find(taskRun => taskRun.id === selectedTaskRunId.value)?.state.current],
        () => playgroundStore.loadContext(execution.value, selectedTaskRunId.value),
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
