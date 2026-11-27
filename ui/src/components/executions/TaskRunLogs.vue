<template>
    <div class="task-run-logs" data-test="task-run-logs">
        <Collapse>
            <KsFormItem class="filter">
                <SearchField
                    :router="false"
                    class="search"
                    @search="logFilter = $event"
                />
            </KsFormItem>
            <KsFormItem class="filter">
                <LogLevelSelector
                    class="level"
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
            :levelFilter="logLevelFilter"
            :hideTaskHeader="hideTaskHeader"
            @follow="emit('follow', $event)"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import type {LevelFilterValue} from "@kestra-io/design-system"
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
    const logLevel = defineModel<LevelKey | undefined>("level", {default: undefined})
    if (logLevel.value === undefined) logLevel.value = defaultTaskRunLogLevel()

    const logLevelFilter = computed<LevelFilterValue>(() => ({value: logLevel.value as LevelKey, direction: "min"}))
</script>

<style scoped>
    .search {
        margin-inline-end: var(--ks-spacing-2);
    }

    @media only screen and (min-width: 768px) {
        .task-run-logs .filter {
            margin-right: 5px;
        }

        .task-run-logs .search,
        .task-run-logs .level {
            width: 200px;
        }
    }

    @media only screen and (max-width: 767px) {
        .task-run-logs .filter {
            display: block;
            margin-right: 0;

            &:last-child {
                margin-bottom: 0;
            }
        }
    }
</style>
