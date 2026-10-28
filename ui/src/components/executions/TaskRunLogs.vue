<template>
    <div class="task-run-logs" data-test="task-run-logs">
        <Collapse>
            <KsFormItem>
                <SearchField
                    :router="false"
                    class="search"
                    @search="logFilter = $event"
                />
            </KsFormItem>
            <KsFormItem>
                <LogLevelSelector
                    :value="logLevel"
                    @update:model-value="logLevel = toLevelKey($event)"
                />
            </KsFormItem>
        </Collapse>
        <TaskRunDetails
            v-for="taskRunId in taskRunIds"
            :key="taskRunId"
            :targetExecutionId="executionId"
            :taskRunId="taskRunId"
            :filter="logFilter"
            :excludeMetas="EXCLUDED_METAS"
            :level="logLevel"
            @follow="emit('follow', $event)"
        />
    </div>
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import {LOG_LEVELS} from "@kestra-io/design-system"
    import SearchField from "../layout/SearchField.vue"
    import Collapse from "../layout/Collapse.vue"
    import LogLevelSelector from "../logs/LogLevelSelector.vue"
    import TaskRunDetails from "../logs/TaskRunDetails.vue"
    import type {LevelKey} from "../../utils/logs"
    import {storageKeys} from "../../utils/constants"

    defineProps<{
        executionId?: string
        taskRunIds: string[]
    }>()

    const emit = defineEmits<{
        follow: [event: unknown]
    }>()

    const EXCLUDED_METAS: ("namespace" | "flowId" | "taskId" | "executionId")[] = ["namespace", "flowId", "taskId", "executionId"]

    const toLevelKey = (value: string | null): LevelKey => LOG_LEVELS.find((level) => level === value) ?? "INFO"

    const logFilter = ref("")
    const logLevel = ref<LevelKey>(toLevelKey(localStorage.getItem(storageKeys.DEFAULT_LOG_LEVEL)))
</script>

<style scoped>
    .search {
        margin-inline-end: var(--ks-spacing-2);
    }
</style>
