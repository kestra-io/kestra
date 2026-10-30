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
            :hideTaskHeader="hideTaskHeader"
            @follow="emit('follow', $event)"
        />
    </div>
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import SearchField from "../layout/SearchField.vue"
    import Collapse from "../layout/Collapse.vue"
    import LogLevelSelector from "../logs/LogLevelSelector.vue"
    import TaskRunDetails from "../logs/TaskRunDetails.vue"
    import type {LevelKey} from "../../utils/logs"
    import {defaultTaskRunLogLevel, toLevelKey} from "./taskRunLogLevel"

    withDefaults(defineProps<{
        executionId?: string
        taskRunIds: string[]
        hideTaskHeader?: boolean
    }>(), {
        executionId: undefined,
        hideTaskHeader: false,
    })

    const emit = defineEmits<{
        follow: [event: unknown]
    }>()

    const EXCLUDED_METAS: ("namespace" | "flowId" | "taskId" | "executionId")[] = ["namespace", "flowId", "taskId", "executionId"]

    const logFilter = ref("")
    const logLevel = ref<LevelKey>(defaultTaskRunLogLevel())
</script>

<style scoped>
    .search {
        margin-inline-end: var(--ks-spacing-2);
    }
</style>
